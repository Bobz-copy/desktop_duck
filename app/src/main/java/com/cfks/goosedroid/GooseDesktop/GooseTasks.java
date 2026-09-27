package com.cfks.goosedroid.GooseDesktop;

import com.cfks.goosedroid.SamEngine.*;

/**
 * Task state classes for goose behaviors.
 */
public class GooseTasks {

    /**
     * Speed tiers for movement.
     */
    public enum SpeedTier {
        Walk,
        Run,
        Charge
    }

    /**
     * All possible goose tasks/states.
     */
    public enum GooseTask {
        Wander,
        NabMouse,
        CollectWindow_Meme,
        CollectWindow_Notepad,
        CollectWindow_Donate,
        CollectWindow_DONOTSET,
        TrackMud,
        Count,
        // Pet states
        Sleeping,
        Eating,
        Playing,
        Sad,
        Happy,
        Seeking,
        BeingPetted,
        BeingDragged
    }

    /**
     * Wander task state.
     */
    public static class WanderTask {
        public final float MinPauseTime = 1f;
        public final float MaxPauseTime = 2f;
        public final float GoodEnoughDistance = 20f;

        public double wanderingStartTime;
        public float wanderingDuration;
        public double pauseStartTime;
        public float pauseDuration;

        public static float getRandomPauseDuration() {
            return 1f + (float) SamMath.Rand.nextDouble();
        }

        public static float getRandomWanderDuration(float firstWanderTime, float minTime, float maxTime) {
            if (Time.time < 1f) {
                return firstWanderTime;
            }
            return SamMath.RandomRange(minTime, maxTime);
        }

        public static float getRandomWalkTime() {
            return SamMath.RandomRange(1f, 6f);
        }
    }

    /**
     * Nab mouse task state.
     */
    public static class NabMouseTask {
        public Stage currentStage;
        public Vector2 dragToPoint;
        public double grabbedOriginalTime;
        public double chaseStartTime;
        public Vector2 originalVectorToMouse;

        public final float MouseGrabDistance = 15f;
        public final float MouseSuccTime = 0.06f;
        public final float MouseDropDistance = 30f;
        public final float MinRunTime = 2f;
        public final float MaxRunTime = 4f;
        public final float GiveUpTime = 9f;

        public static Vector2 StruggleRange = new Vector2(3f, 3f);

        public enum Stage {
            SeekingMouse,
            DraggingMouseAway,
            Decelerating
        }
    }

    /**
     * Collect window task state.
     */
    public static class CollectWindowTask {
        public Stage stage = Stage.WalkingOffscreen;
        public float secsToWait;
        public double waitStartTime;
        public ScreenDirection screenDirection;
        public Vector2 windowOffsetToBeak;

        public static float getWaitTime() {
            return SamMath.RandomRange(2f, 3.5f);
        }

        public enum Stage {
            WalkingOffscreen,
            WaitingToBringWindowBack,
            DraggingWindowBack
        }

        public enum ScreenDirection {
            Left,
            Top,
            Right
        }
    }

    /**
     * Track mud task state.
     */
    public static class TrackMudTask {
        public final float DurationToRunAmok = 2f;
        public double nextDirChangeTime;
        public double timeToStopRunning;
        public Stage stage = Stage.DecideToRun;

        public static float getDirChangeInterval() {
            return 100f;
        }

        public enum Stage {
            DecideToRun,
            RunningOffscreen,
            RunningWandering
        }
    }

}
