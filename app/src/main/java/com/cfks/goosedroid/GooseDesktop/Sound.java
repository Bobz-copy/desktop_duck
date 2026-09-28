/**
 * @Author
 * @AIDE AIDE+
 */
package com.cfks.goosedroid.GooseDesktop;

import android.content.*;
import android.content.res.*;
import android.media.*;
import android.os.Handler;
import android.os.Looper;
import android.util.*;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sound manager with proper resource cleanup to prevent memory leaks.
 */
public class Sound {

    private static final String TAG = "Sound";

    private static MediaPlayer honkBiteSoundPlayer;
    private static MediaPlayer musicPlayer;
    private static MediaPlayer environmentSoundsPlayer;
    private static MediaPlayer[] patSoundPool;
    private static WeakReference<Context> contextRef;
    private static boolean SilenceSounds = false;
    private static boolean isInitialized = false;

    // Track temporary players for cleanup
    private static final CopyOnWriteArrayList<WeakReference<MediaPlayer>> temporaryPlayers =
            new CopyOnWriteArrayList<>();

    // Handler for delayed operations
    private static Handler soundHandler;

    /**
     * Initialize the sound system.
     */
    public static void Init(Context appContext, boolean silenceSounds) {
        if (isInitialized) {
            Log.w(TAG, "Sound already initialized, releasing old resources first");
            releaseAll();
        }

        contextRef = new WeakReference<>(appContext.getApplicationContext());
        Sound.SilenceSounds = silenceSounds;
        soundHandler = new Handler(Looper.getMainLooper());

        // Initialize honkBiteSoundPlayer
        honkBiteSoundPlayer = createMediaPlayerFromAssets("Sound/NotEmbedded/Honk1.mp3");

        // Initialize patSoundPool
        String[] patSources = new String[]{
                "Sound/Pat1.mp3",
                "Sound/Pat2.mp3",
                "Sound/Pat3.mp3"
        };
        patSoundPool = new MediaPlayer[patSources.length];
        for (int i = 0; i < patSources.length; i++) {
            patSoundPool[i] = createMediaPlayerFromAssets(patSources[i]);
        }

        // Initialize environment sound player
        environmentSoundsPlayer = createMediaPlayerFromAssets("Sound/NotEmbedded/MudSquith.mp3");

        // Initialize music player
        String musicPath = "Sound/Music/Music.mp3";
        musicPlayer = createMediaPlayerFromAssets(musicPath);
        if (musicPlayer != null) {
            musicPlayer.setLooping(true);
            setVolume(musicPlayer, 0.5f);
            if (!silenceSounds) {
                musicPlayer.start();
            }
        }

        isInitialized = true;
        Log.i(TAG, "Sound system initialized");
    }

    /**
     * Release all sound resources. Call this when app is being destroyed.
     */
    public static void releaseAll() {
        Log.i(TAG, "Releasing all sound resources");

        // Stop handler
        if (soundHandler != null) {
            soundHandler.removeCallbacksAndMessages(null);
        }

        // Release main players
        releasePlayer(honkBiteSoundPlayer);
        honkBiteSoundPlayer = null;

        releasePlayer(musicPlayer);
        musicPlayer = null;

        releasePlayer(environmentSoundsPlayer);
        environmentSoundsPlayer = null;

        // Release pat sound pool
        if (patSoundPool != null) {
            for (int i = 0; i < patSoundPool.length; i++) {
                releasePlayer(patSoundPool[i]);
                patSoundPool[i] = null;
            }
            patSoundPool = null;
        }

        // Release temporary players
        cleanupTemporaryPlayers(true);

        contextRef = null;
        isInitialized = false;
        Log.i(TAG, "All sound resources released");
    }

    /**
     * Safely release a MediaPlayer.
     */
    private static void releasePlayer(MediaPlayer player) {
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    player.stop();
                }
                player.release();
            } catch (Exception e) {
                Log.w(TAG, "Error releasing MediaPlayer", e);
            }
        }
    }

    /**
     * Clean up temporary players that have finished playing.
     */
    private static void cleanupTemporaryPlayers(boolean forceAll) {
        Iterator<WeakReference<MediaPlayer>> iterator = temporaryPlayers.iterator();
        while (iterator.hasNext()) {
            WeakReference<MediaPlayer> ref = iterator.next();
            MediaPlayer player = ref.get();
            if (player == null) {
                temporaryPlayers.remove(ref);
                continue;
            }
            boolean isFinished;
            try {
                isFinished = !player.isPlaying();
            } catch (IllegalStateException e) {
                // Ya fue liberado por su onCompletion
                isFinished = true;
            }
            if (forceAll || isFinished) {
                releasePlayer(player);
                temporaryPlayers.remove(ref);
            }
        }
    }

    /**
     * Create a temporary player that auto-releases after playing.
     */
    private static MediaPlayer createTemporaryPlayer(String assetPath, float volume) {
        MediaPlayer player = createMediaPlayerFromAssets(assetPath);
        if (player != null) {
            setVolume(player, volume);

            // Track for cleanup
            temporaryPlayers.add(new WeakReference<>(player));

            // Set completion listener for auto-release
            player.setOnCompletionListener(mp -> {
                try {
                    mp.release();
                } catch (Exception e) {
                    Log.w(TAG, "Error releasing completed player", e);
                }
            });

            // Set error listener
            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
                try {
                    mp.release();
                } catch (Exception e) {
                    Log.w(TAG, "Error releasing errored player", e);
                }
                return true;
            });

            // Periodic cleanup of finished players
            if (temporaryPlayers.size() > 10) {
                cleanupTemporaryPlayers(false);
            }
        }
        return player;
    }

    // Play Pat sound
    public static void PlayPat() {
        if (SilenceSounds || patSoundPool == null) return;

        int num = new Random().nextInt(patSoundPool.length);
        MediaPlayer soundPlayer = patSoundPool[num];
        if (soundPlayer != null) {
            try {
                soundPlayer.seekTo(0);
                soundPlayer.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing pat sound", e);
            }
        }
    }

    // Alias for HONCC - used by multiple files
    public static void PlayHonk() {
        HONCC();
    }

    // Play HONCC sound
    public static void HONCC() {
        if (SilenceSounds) return;

        int num = new Random().nextInt(4);
        releasePlayer(honkBiteSoundPlayer);
        honkBiteSoundPlayer = createMediaPlayerFromAssets("Sound/NotEmbedded/Honk" + (num + 1) + ".mp3");
        if (honkBiteSoundPlayer != null) {
            setVolume(honkBiteSoundPlayer, 0.8f);
            try {
                honkBiteSoundPlayer.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing honk sound", e);
            }
        }
    }

    // Play CHOMP sound
    public static void CHOMP() {
        if (SilenceSounds) return;

        releasePlayer(honkBiteSoundPlayer);
        honkBiteSoundPlayer = createMediaPlayerFromAssets("Sound/NotEmbedded/BITE.mp3");
        if (honkBiteSoundPlayer != null) {
            setVolume(honkBiteSoundPlayer, 0.07f);
            try {
                honkBiteSoundPlayer.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing chomp sound", e);
            }
        }
    }

    // Play MudSquith sound
    public static void PlayMudSquith() {
        if (SilenceSounds || environmentSoundsPlayer == null) return;

        try {
            environmentSoundsPlayer.seekTo(0);
            environmentSoundsPlayer.start();
        } catch (Exception e) {
            Log.w(TAG, "Error playing mud sound", e);
        }
    }

    // Play eat sound
    public static void PlayEat() {
        if (SilenceSounds || patSoundPool == null || patSoundPool.length == 0) return;

        MediaPlayer soundPlayer = patSoundPool[0];
        if (soundPlayer != null) {
            try {
                setVolume(soundPlayer, 0.6f);
                soundPlayer.seekTo(0);
                soundPlayer.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing eat sound", e);
            }
        }
    }

    // Play happy sound
    public static void PlayHappy() {
        if (SilenceSounds) return;

        MediaPlayer player = createTemporaryPlayer("Sound/NotEmbedded/Honk1.mp3", 0.4f);
        if (player != null) {
            try {
                player.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing happy sound", e);
            }
        }
    }

    // Play sad sound
    public static void PlaySad() {
        if (SilenceSounds) return;

        MediaPlayer player = createTemporaryPlayer("Sound/NotEmbedded/Honk2.mp3", 0.3f);
        if (player != null) {
            try {
                player.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing sad sound", e);
            }
        }
    }

    // Play sleep sound
    public static void PlaySleep() {
        if (SilenceSounds || patSoundPool == null || patSoundPool.length <= 1) return;

        MediaPlayer soundPlayer = patSoundPool[1];
        if (soundPlayer != null) {
            try {
                setVolume(soundPlayer, 0.2f);
                soundPlayer.seekTo(0);
                soundPlayer.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing sleep sound", e);
            }
        }
    }

    // Play combo sound
    public static void PlayCombo(int comboCount) {
        if (SilenceSounds) return;

        float volume = Math.min(0.3f + (comboCount * 0.1f), 0.8f);
        int honkNum = Math.min(comboCount, 4);

        MediaPlayer player = createTemporaryPlayer("Sound/NotEmbedded/Honk" + honkNum + ".mp3", volume);
        if (player != null) {
            try {
                player.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing combo sound", e);
            }
        }
    }

    // Play achievement sound
    public static void PlayAchievement() {
        if (SilenceSounds || soundHandler == null) return;

        HONCC();
        soundHandler.postDelayed(() -> {
            if (!SilenceSounds) {
                MediaPlayer player = createTemporaryPlayer("Sound/NotEmbedded/Honk1.mp3", 0.6f);
                if (player != null) {
                    try {
                        player.start();
                    } catch (Exception e) {
                        Log.w(TAG, "Error playing achievement sound", e);
                    }
                }
            }
        }, 200);
    }

    // Play jump sound
    public static void PlayJump() {
        if (SilenceSounds) return;

        MediaPlayer player = createTemporaryPlayer("Sound/NotEmbedded/Honk3.mp3", 0.5f);
        if (player != null) {
            try {
                player.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing jump sound", e);
            }
        }
    }

    // Play land sound
    public static void PlayLand() {
        if (SilenceSounds || patSoundPool == null || patSoundPool.length <= 2) return;

        MediaPlayer soundPlayer = patSoundPool[2];
        if (soundPlayer != null) {
            try {
                setVolume(soundPlayer, 0.7f);
                soundPlayer.seekTo(0);
                soundPlayer.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing land sound", e);
            }
        }
    }

    // Play bounce sound
    public static void PlayBounce() {
        if (SilenceSounds) return;

        MediaPlayer player = createTemporaryPlayer("Sound/NotEmbedded/Honk2.mp3", 0.4f);
        if (player != null) {
            try {
                player.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing bounce sound", e);
            }
        }
    }

    // Play thrown sound
    public static void PlayThrown() {
        if (SilenceSounds) return;

        MediaPlayer player = createTemporaryPlayer("Sound/NotEmbedded/Honk4.mp3", 0.6f);
        if (player != null) {
            try {
                player.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing thrown sound", e);
            }
        }
    }

    // Play hungry sound
    public static void PlayHungry() {
        if (SilenceSounds) return;

        MediaPlayer player = createTemporaryPlayer("Sound/NotEmbedded/Honk2.mp3", 0.35f);
        if (player != null) {
            try {
                player.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing hungry sound", e);
            }
        }
    }

    // Play play sound
    public static void PlayPlay() {
        if (SilenceSounds) return;

        MediaPlayer player = createTemporaryPlayer("Sound/NotEmbedded/Honk1.mp3", 0.5f);
        if (player != null) {
            try {
                player.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing play sound", e);
            }
        }
    }

    // Play minigame score sound
    public static void PlayScore() {
        if (SilenceSounds || patSoundPool == null || patSoundPool.length == 0) return;

        MediaPlayer soundPlayer = patSoundPool[0];
        if (soundPlayer != null) {
            try {
                setVolume(soundPlayer, 0.4f);
                soundPlayer.seekTo(0);
                soundPlayer.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing score sound", e);
            }
        }
    }

    // Play minigame win sound
    public static void PlayWin() {
        if (SilenceSounds) return;
        PlayAchievement();
    }

    // Play minigame lose sound
    public static void PlayLose() {
        if (SilenceSounds) return;
        PlaySad();
    }

    // Play boop sound
    public static void PlayBoop() {
        if (SilenceSounds) return;

        MediaPlayer player = createTemporaryPlayer("Sound/NotEmbedded/Honk1.mp3", 0.3f);
        if (player != null) {
            try {
                player.start();
            } catch (Exception e) {
                Log.w(TAG, "Error playing boop sound", e);
            }
        }
    }

    // Play tickle sound
    public static void PlayTickle() {
        if (SilenceSounds || soundHandler == null || patSoundPool == null) return;

        for (int i = 0; i < 3; i++) {
            final int delay = i * 100;
            soundHandler.postDelayed(() -> {
                if (!SilenceSounds && patSoundPool != null && patSoundPool.length > 0) {
                    int idx = new Random().nextInt(patSoundPool.length);
                    MediaPlayer soundPlayer = patSoundPool[idx];
                    if (soundPlayer != null) {
                        try {
                            setVolume(soundPlayer, 0.3f);
                            soundPlayer.seekTo(0);
                            soundPlayer.start();
                        } catch (Exception e) {
                            Log.w(TAG, "Error playing tickle sound", e);
                        }
                    }
                }
            }, delay);
        }
    }

    // Stop background music
    public static void StopMusic() {
        if (musicPlayer != null) {
            try {
                if (musicPlayer.isPlaying()) {
                    musicPlayer.stop();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error stopping music", e);
            }
        }
    }

    // Resume background music
    public static void ResumeMusic() {
        if (musicPlayer != null && !SilenceSounds) {
            try {
                musicPlayer.prepare();
                musicPlayer.start();
            } catch (Exception e) {
                Log.w(TAG, "Error resuming music", e);
            }
        }
    }

    // Set silence mode
    public static void setSilenceMode(boolean silence) {
        SilenceSounds = silence;
        if (silence) {
            StopMusic();
        } else {
            ResumeMusic();
        }
    }

    // Check if sounds are silenced
    public static boolean isSilenced() {
        return SilenceSounds;
    }

    // Set volume
    private static void setVolume(MediaPlayer mediaPlayer, float volume) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setVolume(volume, volume);
            } catch (Exception e) {
                Log.w(TAG, "Error setting volume", e);
            }
        }
    }

    /**
     * Create MediaPlayer from assets with proper resource cleanup.
     */
    private static MediaPlayer createMediaPlayerFromAssets(String filePath) {
        Context context = contextRef != null ? contextRef.get() : null;
        if (context == null) {
            Log.e(TAG, "Context is null, cannot load sound: " + filePath);
            return null;
        }

        AssetFileDescriptor fd = null;
        try {
            fd = context.getAssets().openFd(filePath);
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            mediaPlayer.prepare();
            return mediaPlayer;
        } catch (IOException e) {
            Log.e(TAG, "Error loading sound file: " + filePath, e);
            return null;
        } finally {
            // Always close the AssetFileDescriptor
            if (fd != null) {
                try {
                    fd.close();
                } catch (IOException e) {
                    Log.w(TAG, "Error closing AssetFileDescriptor", e);
                }
            }
        }
    }
}
