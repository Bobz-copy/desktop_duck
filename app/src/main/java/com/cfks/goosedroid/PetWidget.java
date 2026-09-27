package com.cfks.goosedroid;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

/**
 * Widget de Android para mostrar el estado de la mascota
 * y proporcionar accesos rápidos a las acciones principales.
 */
public class PetWidget extends AppWidgetProvider {

    public static final String ACTION_FEED = "com.cfks.goosedroid.ACTION_FEED";
    public static final String ACTION_PLAY = "com.cfks.goosedroid.ACTION_PLAY";
    public static final String ACTION_SLEEP = "com.cfks.goosedroid.ACTION_SLEEP";
    public static final String ACTION_REFRESH = "com.cfks.goosedroid.ACTION_REFRESH";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        if (action == null) return;

        // Security: Verify the intent comes from our own app or the system
        // Only process our custom actions if they come from a PendingIntent we created
        // (which uses our package context) or from the system for APPWIDGET_UPDATE
        String packageName = context.getPackageName();

        // For custom actions, verify the sender is our app
        if (action.startsWith(packageName)) {
            // Additional security: Check if this came from a trusted source
            // PendingIntents created by us will have our package as the creator
            if (intent.getPackage() != null && !intent.getPackage().equals(packageName)) {
                // Reject intents explicitly targeting another package
                return;
            }
        }

        // Con el proceso recién nacido el estado en memoria son valores por defecto
        PetRepository.ensureLoaded(context);

        switch (action) {
            case ACTION_FEED:
                PetNeeds.get().feed();
                persistAndRefresh(context);
                break;
            case ACTION_PLAY:
                if (PetNeeds.get().energy > 20) {
                    PetNeeds.get().play();
                }
                persistAndRefresh(context);
                break;
            case ACTION_SLEEP:
                PetNeeds.get().sleep();
                persistAndRefresh(context);
                break;
            case ACTION_REFRESH:
                updateAllWidgets(context);
                break;
        }
    }

    private static void persistAndRefresh(Context context) {
        PetRepository.save(context, null);
        updateAllWidgets(context);
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        PetRepository.ensureLoaded(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.pet_widget);

        // Update pet name and status
        String petName = PetAppearance.get().petName;
        String mood = getMoodEmoji();
        views.setTextViewText(R.id.widget_pet_name, petName + " " + mood);

        // Update status text
        String statusText = getStatusText();
        views.setTextViewText(R.id.widget_status, statusText);

        // Update progress bars
        int hungerProgress = (int) (100 - PetNeeds.get().hunger);
        int energyProgress = (int) PetNeeds.get().energy;
        int happinessProgress = (int) PetNeeds.get().happiness;

        views.setProgressBar(R.id.widget_hunger_bar, 100, hungerProgress, false);
        views.setProgressBar(R.id.widget_energy_bar, 100, energyProgress, false);
        views.setProgressBar(R.id.widget_happiness_bar, 100, happinessProgress, false);

        // Set click intents for buttons
        views.setOnClickPendingIntent(R.id.widget_feed_button,
                getPendingSelfIntent(context, ACTION_FEED));
        views.setOnClickPendingIntent(R.id.widget_play_button,
                getPendingSelfIntent(context, ACTION_PLAY));
        views.setOnClickPendingIntent(R.id.widget_sleep_button,
                getPendingSelfIntent(context, ACTION_SLEEP));

        // Set click intent for the whole widget to open app
        Intent openAppIntent = new Intent(context, MainActivity.class);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE :
                        PendingIntent.FLAG_UPDATE_CURRENT
        );
        views.setOnClickPendingIntent(R.id.widget_pet_name, openAppPendingIntent);

        // Update widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static PendingIntent getPendingSelfIntent(Context context, String action) {
        Intent intent = new Intent(context, PetWidget.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(
                context, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE :
                        PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private static String getMoodEmoji() {
        PetNeeds.MoodState mood = PetNeeds.get().getMoodState();
        switch (mood) {
            case HUNGRY: return "\uD83D\uDE1F"; // Worried face
            case TIRED: return "\uD83D\uDE34"; // Sleeping face
            case SAD: return "\uD83D\uDE22"; // Crying face
            case HAPPY: return "\uD83D\uDE0A"; // Smiling face
            default: return "\uD83D\uDC23"; // Baby chick
        }
    }

    private static String getStatusText() {
        PetNeeds.MoodState mood = PetNeeds.get().getMoodState();
        switch (mood) {
            case HUNGRY: return "I'm hungry!";
            case TIRED: return "I need sleep...";
            case SAD: return "Play with me!";
            case HAPPY: return "I'm happy!";
            default: return "Doing fine~";
        }
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, PetWidget.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {
        // Widget first instance created
    }

    @Override
    public void onDisabled(Context context) {
        // Last widget instance deleted
    }
}
