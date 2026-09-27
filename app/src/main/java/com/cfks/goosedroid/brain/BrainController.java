package com.cfks.goosedroid.brain;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.cfks.goosedroid.GooseDesktop.GooseAI;
import com.cfks.goosedroid.GooseDesktop.GooseLLM;
import com.cfks.goosedroid.GooseDesktop.GooseSystemReactions;
import com.cfks.goosedroid.GooseDesktop.GooseTasks;
import com.cfks.goosedroid.GooseDesktop.TheGoose;
import com.cfks.goosedroid.GooseEvolution;
import com.cfks.goosedroid.PetAppearance;
import com.cfks.goosedroid.PetNeeds;
import com.cfks.goosedroid.PetPersonality;
import com.cfks.goosedroid.brain.backend.TemplateBackend;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Une el cerebro con el juego: arma la foto del estado, decide cuándo disparar
 * cada tipo de pensamiento y traduce las intenciones en cosas que el ganso hace.
 *
 * Todos los métodos se llaman desde el hilo principal.
 */
public final class BrainController {
    private static final String TAG = "BrainController";
    private static final String MEMORY_FILE = "brain/memory.json";
    private static final String DIARY_DATE_FORMAT = "yyyy-MM-dd";

    private static final int MAX_RECENT_EVENTS = 8;
    /** Con plantillas pensar es gratis: se mantiene el ritmo original del juego. */
    private static final long TEMPLATE_MIN_INTERVAL_MS = 6_000L;
    private static final float SLOW_CHECK_INTERVAL_SECONDS = 30f;
    private static final long NEED_ALERT_COOLDOWN_MS = 10 * 60_000L;
    private static final int DIARY_HOUR = 21;
    private static final float CRITICAL_HUNGER = 90f;
    private static final float CRITICAL_ENERGY = 10f;
    private static final float CRITICAL_HAPPINESS = 10f;

    /** Para pantallas que quieren ver lo que el ganso contesta (chat, prueba). */
    public interface ReplyListener {
        void onReply(BrainIntent intent, String backendId);

        void onError(LlmException error, String backendId);
    }

    private static Context appContext;
    private static BrainConfig config;
    private static BrainMemory memory;
    private static GooseBrain brain;
    private static ReplyListener replyListener;
    private static final List<String> recentEvents = new ArrayList<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static float slowCheckTimer = 0f;
    private static long lastNeedAlertMs = 0L;
    private static boolean wasSleeping = false;

    private BrainController() {
    }

    // ============== CICLO DE VIDA ==============

    public static void start(Context context) {
        ensureInitialized(context);
        rebuildBrain();
        slowCheckTimer = 0f;
        wasSleeping = false;
    }

    public static void stop() {
        if (brain != null) {
            brain.release();
            brain = null;
        }
        recentEvents.clear();
    }

    /** Vuelve a leer la configuración; llamar después de cambiarla. */
    public static void reloadConfig(Context context) {
        ensureInitialized(context);
        if (brain != null) {
            brain.release();
        }
        rebuildBrain();
    }

    private static void ensureInitialized(Context context) {
        if (appContext != null) return;
        appContext = context.getApplicationContext();
        config = new BrainConfig(appContext);
        memory = new BrainMemory(new File(appContext.getFilesDir(), MEMORY_FILE));
    }

    private static void rebuildBrain() {
        LlmBackend primary = BackendCatalog.create(config);
        LlmBackend fallback = new TemplateBackend(BrainController::templatePhrase);

        brain = new GooseBrain(primary, fallback, new PromptBuilder(config.getLanguage()),
                memory, mainHandler::post, android.os.SystemClock::elapsedRealtime);
        brain.setMinIntervalMs(primary == null
                ? TEMPLATE_MIN_INTERVAL_MS : config.getIntervalSeconds() * 1000L);
        brain.setListener(new GooseBrain.Listener() {
            @Override
            public void onIntent(BrainIntent intent, BrainTrigger trigger, String backendId) {
                applyIntent(intent, trigger, backendId);
            }

            @Override
            public void onBackendError(LlmException error, String backendId) {
                Log.w(TAG, "Backend " + backendId + " falló ("
                        + error.getKind() + "): " + error.getMessage());
                if (replyListener != null) {
                    replyListener.onError(error, backendId);
                }
            }
        });
    }

    /** Las plantillas del juego original, como respaldo. */
    private static String templatePhrase(String tag) {
        final String[] result = new String[1];
        GooseLLM.ThoughtCallback capture = thought -> result[0] = thought;

        if (BrainTrigger.Kind.PETTED.name().equals(tag)) {
            GooseLLM.generateResponse("pet", capture);
        } else if (BrainTrigger.Kind.FED.name().equals(tag)) {
            GooseLLM.generateResponse("feed", capture);
        } else if (BrainTrigger.Kind.PLAYED.name().equals(tag)) {
            GooseLLM.generateResponse("play", capture);
        } else if (BrainTrigger.Kind.DRAGGED.name().equals(tag)) {
            GooseLLM.generateResponse("drag", capture);
        } else if (BrainTrigger.Kind.GREETING.name().equals(tag)) {
            GooseLLM.generateResponse("hello", capture);
        } else if (BrainTrigger.Kind.DIARY.name().equals(tag)) {
            // Un diario hecho con plantillas no aporta nada: mejor no escribirlo
            return null;
        } else {
            GooseLLM.generateThought(appContext, capture);
        }
        return result[0];
    }

    // ============== ENTRADAS ==============

    public static BrainMemory getMemory(Context context) {
        ensureInitialized(context);
        return memory;
    }

    public static void setReplyListener(ReplyListener listener) {
        replyListener = listener;
    }

    public static boolean isRunning() {
        return brain != null;
    }

    public static String getActiveBackendId() {
        return brain != null ? brain.getActiveBackendId() : TemplateBackend.ID;
    }

    /** Anota algo que pasó, para que el ganso lo tenga presente al pensar. */
    public static void recordEvent(String description) {
        if (description == null || description.trim().isEmpty()) return;
        recentEvents.add(description.trim());
        while (recentEvents.size() > MAX_RECENT_EVENTS) {
            recentEvents.remove(0);
        }
    }

    /**
     * @return true si el ganso empezó a pensar
     */
    public static boolean requestThought(BrainTrigger.Kind kind, String detail) {
        if (brain == null || !TheGoose.isRunning()) return false;
        recordTriggerAsEvent(kind);
        return brain.think(buildSnapshot(), new BrainTrigger(kind, detail));
    }

    private static void recordTriggerAsEvent(BrainTrigger.Kind kind) {
        switch (kind) {
            case PETTED:
                recordEvent("tu humano te acarició");
                break;
            case FED:
                recordEvent("tu humano te dio de comer");
                break;
            case PLAYED:
                recordEvent("jugaste con tu humano");
                break;
            case DRAGGED:
                recordEvent("tu humano te arrastró por la pantalla");
                break;
            default:
                break;
        }
    }

    /** Chequeos lentos: necesidades críticas, sueño y diario. */
    public static void onTick(float deltaTime) {
        if (brain == null) return;
        slowCheckTimer += deltaTime;

        boolean isSleeping = GooseTasks.GooseTask.Sleeping.name()
                .equals(TheGoose.getCurrentTaskName());
        if (isSleeping && !wasSleeping) {
            requestThought(BrainTrigger.Kind.DREAM, "");
        }
        wasSleeping = isSleeping;

        if (slowCheckTimer < SLOW_CHECK_INTERVAL_SECONDS) return;
        slowCheckTimer = 0f;

        checkCriticalNeeds();
        checkDiary();
    }

    private static void checkCriticalNeeds() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastNeedAlertMs < NEED_ALERT_COOLDOWN_MS) return;

        PetNeeds needs = PetNeeds.get();
        String detail = null;
        if (needs.hunger > CRITICAL_HUNGER) {
            detail = "hambre";
        } else if (needs.energy < CRITICAL_ENERGY) {
            detail = "cansancio";
        } else if (needs.happiness < CRITICAL_HAPPINESS) {
            detail = "tristeza";
        }
        if (detail != null && requestThought(BrainTrigger.Kind.NEED_CRITICAL, detail)) {
            lastNeedAlertMs = now;
        }
    }

    private static void checkDiary() {
        if (brain.getActiveBackendId().equals(TemplateBackend.ID)) return;
        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < DIARY_HOUR) return;
        if (memory.hasDiaryEntryFor(today())) return;
        requestThought(BrainTrigger.Kind.DIARY, "");
    }

    private static String today() {
        return new SimpleDateFormat(DIARY_DATE_FORMAT, Locale.ROOT).format(new Date());
    }

    // ============== FOTO DEL ESTADO ==============

    static PetSnapshot buildSnapshot() {
        PetNeeds needs = PetNeeds.get();
        PetPersonality personality = PetPersonality.get();
        Calendar now = Calendar.getInstance();
        String dayName = now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG,
                new Locale("es"));

        return PetSnapshot.builder()
                .petName(PetAppearance.get().petName)
                .needs(needs.hunger, needs.energy, needs.happiness)
                .personality(personality.playfulness, personality.affection,
                        personality.bravery, personality.mischief)
                .stage(GooseEvolution.getCurrentStage().displayName,
                        GooseEvolution.getAgeMinutes())
                .currentActivity(describeTask(TheGoose.getCurrentTaskName()))
                .clock(now.get(Calendar.HOUR_OF_DAY), dayName)
                .battery(GooseSystemReactions.getBatteryLevel(),
                        GooseSystemReactions.isCharging())
                .recentEvents(recentEvents)
                .build();
    }

    private static String describeTask(String taskName) {
        if (taskName == null || taskName.isEmpty()) return "";
        switch (taskName) {
            case "Wander":
                return "paseando";
            case "Sleeping":
                return "durmiendo";
            case "Eating":
                return "comiendo";
            case "Playing":
                return "jugando";
            case "Sad":
                return "triste en un rincón";
            case "Happy":
                return "festejando";
            case "Seeking":
                return "buscando a tu humano";
            case "TrackMud":
                return "dejando huellas de barro";
            case "BeingPetted":
                return "recibiendo caricias";
            case "BeingDragged":
                return "en el aire, agarrado por tu humano";
            default:
                return "haciendo de las tuyas";
        }
    }

    // ============== SALIDAS ==============

    private static void applyIntent(BrainIntent intent, BrainTrigger trigger, String backendId) {
        Log.d(TAG, "[" + backendId + "] " + trigger.kind + " -> say=\"" + intent.say
                + "\" mood=" + intent.mood + " action=" + intent.action);
        if (replyListener != null) {
            replyListener.onReply(intent, backendId);
        }

        if (trigger.kind == BrainTrigger.Kind.DIARY) {
            if (intent.hasSpeech()) {
                memory.writeDiary(today(), intent.say);
                TheGoose.setThought("*escribe en su diario*");
            }
            return;
        }

        if (!TheGoose.isRunning()) return;

        if (intent.hasSpeech()) {
            TheGoose.setThought(intent.say);
            recordEvent("dijiste: " + intent.say);
        } else {
            TheGoose.showEmoji(emojiFor(intent.mood));
        }
        performAction(intent.action);
    }

    private static String emojiFor(BrainMood mood) {
        switch (mood) {
            case HAPPY:
                return ":)";
            case EXCITED:
                return "!!!";
            case LOVING:
                return "<3";
            case SLEEPY:
                return "zzz";
            case HUNGRY:
                return "pan?";
            case SAD:
                return ":(";
            case ANGRY:
                return ">:(";
            case MISCHIEVOUS:
                return ">:)";
            case CURIOUS:
                return "?";
            case SCARED:
                return "!!";
            case NEUTRAL:
            default:
                return "";
        }
    }

    private static void performAction(BrainAction action) {
        switch (action) {
            case WANDER:
                TheGoose.requestTask(GooseTasks.GooseTask.Wander);
                break;
            case NAP:
                TheGoose.requestTask(GooseTasks.GooseTask.Sleeping);
                break;
            case TRACK_MUD:
                TheGoose.requestTask(GooseTasks.GooseTask.TrackMud);
                break;
            case SEEK_ATTENTION:
                TheGoose.requestTask(GooseTasks.GooseTask.Seeking);
                break;
            case SULK:
                TheGoose.requestTask(GooseTasks.GooseTask.Sad);
                break;
            case CELEBRATE:
                TheGoose.requestTask(GooseTasks.GooseTask.Happy);
                break;
            case ZOOMIES:
                TheGoose.requestEvent(GooseAI.RandomEvent.ZOOMIES);
                break;
            case DANCE:
                TheGoose.requestEvent(GooseAI.RandomEvent.DANCE);
                break;
            case SPIN:
                TheGoose.requestEvent(GooseAI.RandomEvent.SPIN);
                break;
            case SING:
                TheGoose.requestEvent(GooseAI.RandomEvent.SINGING);
                break;
            case HONK:
                TheGoose.requestEvent(GooseAI.RandomEvent.RANDOM_HONK);
                break;
            case STRETCH:
                TheGoose.requestEvent(GooseAI.RandomEvent.STRETCH);
                break;
            case LOOK_AROUND:
                TheGoose.requestEvent(GooseAI.RandomEvent.LOOK_AROUND);
                break;
            case PLAY_DEAD:
                TheGoose.requestEvent(GooseAI.RandomEvent.PLAY_DEAD);
                break;
            case MOONWALK:
                TheGoose.requestEvent(GooseAI.RandomEvent.MOONWALK);
                break;
            case NONE:
            default:
                break;
        }
    }
}
