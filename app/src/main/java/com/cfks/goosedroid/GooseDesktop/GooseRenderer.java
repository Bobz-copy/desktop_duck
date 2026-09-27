package com.cfks.goosedroid.GooseDesktop;

import android.graphics.*;
import com.cfks.goosedroid.SamEngine.*;
import com.cfks.goosedroid.PetAppearance;
import com.cfks.goosedroid.PetNeeds;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Advanced rendering system for the goose.
 * Handles body rendering, particles, trails, glow effects, and status indicators.
 */
public class GooseRenderer {

    // ============== PARTICLE SYSTEM ==============

    /**
     * Represents a single particle in the system.
     */
    public static class Particle {
        public Vector2 position;
        public Vector2 velocity;
        public float life;          // 0 to 1, dies at 0
        public float maxLife;
        public float size;
        public int color;
        public ParticleType type;
        public float rotation;
        public float rotationSpeed;
        public float alpha;

        public Particle(Vector2 pos, Vector2 vel, float life, float size, int color, ParticleType type) {
            this.position = new Vector2(pos.x, pos.y);
            this.velocity = vel;
            this.life = life;
            this.maxLife = life;
            this.size = size;
            this.color = color;
            this.type = type;
            this.rotation = SamMath.RandomRange(0, 360);
            this.rotationSpeed = SamMath.RandomRange(-180, 180);
            this.alpha = 1f;
        }

        public void update(float deltaTime) {
            position = Vector2.add(position, Vector2.multiply(velocity, deltaTime));
            life -= deltaTime;
            alpha = Math.max(0, life / maxLife);
            rotation += rotationSpeed * deltaTime;

            // Apply gravity for some particle types
            if (type == ParticleType.CONFETTI || type == ParticleType.SWEAT_DROP) {
                velocity.y += 100f * deltaTime;
            }
        }

        public boolean isDead() {
            return life <= 0;
        }
    }

    /**
     * Types of particles available.
     */
    public enum ParticleType {
        HEART,          // Floating hearts when happy/petted
        STAR,           // Stars when excited/playing
        MUSIC_NOTE,     // Notes when dancing
        CONFETTI,       // Celebration particles
        BUBBLE,         // Bubbles when eating
        SWEAT_DROP,     // When tired/stressed
        SPARKLE,        // General sparkle effect
        DUST,           // Movement dust
        ZZZ,            // Sleep particles
        ANGER           // Anger marks
    }

    // ============== TRAIL SYSTEM ==============

    /**
     * Trail point for motion trails.
     */
    public static class TrailPoint {
        public Vector2 position;
        public double time;
        public float alpha;

        public TrailPoint(Vector2 pos, double time) {
            this.position = new Vector2(pos.x, pos.y);
            this.time = time;
            this.alpha = 1f;
        }
    }

    // ============== CONSTANTS ==============

    private static final int MAX_PARTICLES = 100;
    private static final int MAX_TRAIL_POINTS = 20;
    private static final float TRAIL_POINT_INTERVAL = 0.05f;
    private static final float TRAIL_FADE_TIME = 0.5f;
    private static final float SPEED_LINE_THRESHOLD = 150f;
    private static final float GLOW_PULSE_SPEED = 3f;

    // ============== STATE ==============

    private Paint shadowPen;
    private Paint drawingPen;
    private Paint particlePaint;
    private Paint glowPaint;
    private Paint trailPaint;
    private Paint indicatorPaint;

    private List<Particle> particles;
    private List<TrailPoint> trailPoints;
    private double lastTrailTime = 0;
    private float glowPhase = 0;
    private boolean showShadow = true;

    // Glow state
    private int currentGlowColor = 0;
    private float glowIntensity = 0;
    private float targetGlowIntensity = 0;

    // Particle spawn timers
    private float heartSpawnTimer = 0;
    private float starSpawnTimer = 0;
    private float bubbleSpawnTimer = 0;

    // Colors
    public int footColor = 0xFFFFA500;
    public int outlineColor = 0xFFD3D3D3;
    public int mouthColor = 0xFFFFA500;
    public int eyeColor = 0xFF000000;
    public int bodyColor = 0xFFFFFFFF;

    // ============== CONSTRUCTOR ==============

    public GooseRenderer() {
        // Create shadow brush with pattern
        Bitmap shadowBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        shadowBitmap.setPixel(0, 0, Color.TRANSPARENT);
        shadowBitmap.setPixel(1, 1, Color.TRANSPARENT);
        shadowBitmap.setPixel(1, 0, Color.TRANSPARENT);
        shadowBitmap.setPixel(0, 1, 0xFFA9A9A9);

        shadowPen = new Paint();
        shadowPen.setShader(new BitmapShader(shadowBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        shadowPen.setStrokeCap(Paint.Cap.ROUND);

        // Create drawing pen
        drawingPen = new Paint();
        drawingPen.setColor(Color.WHITE);
        drawingPen.setStrokeCap(Paint.Cap.ROUND);
        drawingPen.setAntiAlias(true);

        // Create particle paint
        particlePaint = new Paint();
        particlePaint.setAntiAlias(true);

        // Create glow paint with blur
        glowPaint = new Paint();
        glowPaint.setAntiAlias(true);
        glowPaint.setMaskFilter(new BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL));

        // Create trail paint
        trailPaint = new Paint();
        trailPaint.setAntiAlias(true);
        trailPaint.setStrokeCap(Paint.Cap.ROUND);

        // Create indicator paint
        indicatorPaint = new Paint();
        indicatorPaint.setAntiAlias(true);

        // Initialize collections
        particles = new ArrayList<>();
        trailPoints = new ArrayList<>();
    }

    public void setShowShadow(boolean show) {
        this.showShadow = show;
        if (shadowPen != null) {
            shadowPen.setAlpha(show ? 255 : 0);
        }
    }

    // ============== MAIN RENDER ==============

    /**
     * Main render method.
     */
    /** Capas de dibujo: el mundo (efectos, texto) y el cuerpo del ganso. */
    public enum Layer { WORLD, GOOSE }

    /**
     * Avanza el estado de dibujo. Una vez por frame.
     */
    public void updateFrame(GoosePhysics physics, GooseRig rig, GooseAI ai,
                            boolean petModeEnabled) {
        float deltaTime = Time.deltaTime;

        // Update rig
        rig.update(physics.getPosition(), physics.getDirection());

        // Update systems
        updateParticles(deltaTime);
        updateTrail(physics, deltaTime);
        updateGlow(ai, deltaTime);

        // Spawn particles based on state
        if (petModeEnabled) {
            spawnStateParticles(physics, ai, deltaTime);
        }
    }

    public void render(Canvas canvas, Layer layer, GoosePhysics physics, GooseRig rig,
                       GooseTouchHandler touchHandler, GooseAI ai, boolean petModeEnabled,
                       int worldWidth, int worldHeight) {

        // Calculate direction vectors
        float dir = physics.getDirection();
        Vector2 position = physics.getPosition();
        Vector2 scale = new Vector2(1.3f, 0.4f);
        Vector2 forward = Vector2.multiply(Vector2.GetFromAngleDegrees(dir), scale);
        Vector2 right = Vector2.multiply(Vector2.GetFromAngleDegrees(dir + 90f), scale);

        // Apply global draw scale
        float drawScale = TheGoose.DrawScale;
        canvas.save();
        canvas.translate(position.x, position.y);
        canvas.scale(drawScale, drawScale);
        canvas.translate(-position.x, -position.y);

        // Cache forward direction for shading calculations
        lastForward = forward;

        // Render layers (back to front)
        if (layer == Layer.WORLD) {
            renderFootprints(canvas, physics);
            renderTrail(canvas, physics);
            renderGlow(canvas, position);
            renderParticles(canvas);
            if (petModeEnabled) {
                renderStatusIndicators(canvas, position);
                renderSleepIndicator(canvas, position, ai);
                renderSpeedLines(canvas, physics);
            }
        } else {
            renderFeet(canvas, physics);
            renderShadow(canvas, position);
            renderBody(canvas, rig, forward);
            renderDynamicSpeculars(canvas, rig, physics, forward);
            renderEyes(canvas, rig);
            if (petModeEnabled) {
                renderAccessories(canvas, rig);
            }
        }

        // Restore canvas scale
        canvas.restore();

        if (layer != Layer.WORLD) return;

        // Render emoji outside of scale (so it stays readable)
        if (petModeEnabled) {
            renderEmoji(canvas, position, touchHandler);
            renderThoughtBubble(canvas, position);
        }

        // Render achievement notifications at top of screen
        renderAchievementNotification(canvas, worldWidth, worldHeight);
    }

    // ============== PARTICLE MANAGEMENT ==============

    private void updateParticles(float deltaTime) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update(deltaTime);
            if (p.isDead()) {
                it.remove();
            }
        }
    }

    /**
     * Spawn a single particle.
     */
    public void spawnParticle(Vector2 position, ParticleType type) {
        if (particles.size() >= MAX_PARTICLES) return;

        Vector2 vel;
        float life, size;
        int color;

        switch (type) {
            case HEART:
                vel = new Vector2(SamMath.RandomRange(-20, 20), SamMath.RandomRange(-50, -30));
                life = SamMath.RandomRange(1f, 2f);
                size = SamMath.RandomRange(8, 14);
                color = 0xFFFF69B4;
                break;
            case STAR:
                vel = new Vector2(SamMath.RandomRange(-40, 40), SamMath.RandomRange(-60, -20));
                life = SamMath.RandomRange(0.8f, 1.5f);
                size = SamMath.RandomRange(6, 12);
                color = 0xFFFFD700;
                break;
            case MUSIC_NOTE:
                vel = new Vector2(SamMath.RandomRange(-15, 15), SamMath.RandomRange(-40, -25));
                life = SamMath.RandomRange(1.5f, 2.5f);
                size = SamMath.RandomRange(10, 16);
                color = 0xFF9370DB;
                break;
            case CONFETTI:
                vel = new Vector2(SamMath.RandomRange(-80, 80), SamMath.RandomRange(-100, -50));
                life = SamMath.RandomRange(1f, 2f);
                size = SamMath.RandomRange(4, 8);
                // Random bright color
                int[] confettiColors = {0xFFFF6B6B, 0xFF4ECDC4, 0xFFFFE66D, 0xFF95E1D3, 0xFFDDA0DD};
                color = confettiColors[(int)(Math.random() * confettiColors.length)];
                break;
            case BUBBLE:
                vel = new Vector2(SamMath.RandomRange(-10, 10), SamMath.RandomRange(-30, -15));
                life = SamMath.RandomRange(0.8f, 1.2f);
                size = SamMath.RandomRange(4, 10);
                color = 0x8800BFFF;
                break;
            case SWEAT_DROP:
                vel = new Vector2(SamMath.RandomRange(-5, 5), -20);
                life = SamMath.RandomRange(0.6f, 1f);
                size = SamMath.RandomRange(4, 8);
                color = 0xFF87CEEB;
                break;
            case SPARKLE:
                vel = new Vector2(SamMath.RandomRange(-30, 30), SamMath.RandomRange(-30, 30));
                life = SamMath.RandomRange(0.3f, 0.6f);
                size = SamMath.RandomRange(3, 6);
                color = 0xFFFFFFFF;
                break;
            case DUST:
                vel = new Vector2(SamMath.RandomRange(-20, 20), SamMath.RandomRange(-10, 5));
                life = SamMath.RandomRange(0.3f, 0.5f);
                size = SamMath.RandomRange(2, 5);
                color = 0x88C4A484;
                break;
            case ZZZ:
                vel = new Vector2(15, -25);
                life = SamMath.RandomRange(1.5f, 2f);
                size = SamMath.RandomRange(12, 18);
                color = 0xFF6666FF;
                break;
            case ANGER:
                vel = new Vector2(SamMath.RandomRange(-25, 25), SamMath.RandomRange(-35, -20));
                life = SamMath.RandomRange(0.5f, 0.8f);
                size = SamMath.RandomRange(6, 10);
                color = 0xFFFF4444;
                break;
            default:
                return;
        }

        particles.add(new Particle(position, vel, life, size, color, type));
    }

    /**
     * Spawn burst of particles.
     */
    public void spawnParticleBurst(Vector2 position, ParticleType type, int count) {
        for (int i = 0; i < count; i++) {
            Vector2 offset = new Vector2(
                SamMath.RandomRange(-15, 15),
                SamMath.RandomRange(-15, 15)
            );
            spawnParticle(Vector2.add(position, offset), type);
        }
    }

    /**
     * Spawn particles based on current state.
     */
    private void spawnStateParticles(GoosePhysics physics, GooseAI ai, float deltaTime) {
        Vector2 pos = physics.getPosition();
        GooseTasks.GooseTask task = ai.getCurrentTask();
        PetNeeds.MoodState mood = PetNeeds.get().getMoodState();

        // Heart particles when being petted or happy
        if (task == GooseTasks.GooseTask.BeingPetted) {
            heartSpawnTimer -= deltaTime;
            if (heartSpawnTimer <= 0) {
                spawnParticle(Vector2.add(pos, new Vector2(0, -40)), ParticleType.HEART);
                heartSpawnTimer = 0.3f;
            }
        }

        // Star particles when playing or excited
        if (task == GooseTasks.GooseTask.Playing || task == GooseTasks.GooseTask.Happy) {
            starSpawnTimer -= deltaTime;
            if (starSpawnTimer <= 0) {
                spawnParticle(Vector2.add(pos, new Vector2(SamMath.RandomRange(-20, 20), -35)), ParticleType.STAR);
                starSpawnTimer = 0.5f;
            }
        }

        // Bubble particles when eating
        if (task == GooseTasks.GooseTask.Eating) {
            bubbleSpawnTimer -= deltaTime;
            if (bubbleSpawnTimer <= 0) {
                spawnParticle(Vector2.add(pos, new Vector2(SamMath.RandomRange(-10, 10), -30)), ParticleType.BUBBLE);
                bubbleSpawnTimer = 0.4f;
            }
        }

        // Sweat drops when tired
        if (mood == PetNeeds.MoodState.TIRED && Math.random() < 0.02) {
            spawnParticle(Vector2.add(pos, new Vector2(15, -35)), ParticleType.SWEAT_DROP);
        }

        // Dust when moving fast
        if (physics.getCurrentSpeed() > 100f && Math.random() < 0.1) {
            spawnParticle(pos, ParticleType.DUST);
        }

        // Music notes during dance event
        if (ai.getActiveEvent() == GooseAI.RandomEvent.DANCE) {
            if (Math.random() < 0.08) {
                spawnParticle(Vector2.add(pos, new Vector2(SamMath.RandomRange(-20, 20), -40)), ParticleType.MUSIC_NOTE);
            }
        }

        // ZZZ when sleeping
        if (task == GooseTasks.GooseTask.Sleeping && Math.random() < 0.03) {
            spawnParticle(Vector2.add(pos, new Vector2(20, -45)), ParticleType.ZZZ);
        }
    }

    private void renderParticles(Canvas canvas) {
        for (Particle p : particles) {
            particlePaint.setColor(p.color);
            particlePaint.setAlpha((int)(p.alpha * 255));

            canvas.save();
            canvas.translate(p.position.x, p.position.y);
            canvas.rotate(p.rotation);

            switch (p.type) {
                case HEART:
                    drawHeart(canvas, particlePaint, Vector2.zero, p.size);
                    break;
                case STAR:
                    drawStar(canvas, particlePaint, Vector2.zero, p.size);
                    break;
                case MUSIC_NOTE:
                    drawMusicNote(canvas, particlePaint, Vector2.zero, p.size);
                    break;
                case CONFETTI:
                    particlePaint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(-p.size/2, -p.size/4, p.size/2, p.size/4, particlePaint);
                    break;
                case BUBBLE:
                    particlePaint.setStyle(Paint.Style.STROKE);
                    particlePaint.setStrokeWidth(1.5f);
                    canvas.drawCircle(0, 0, p.size, particlePaint);
                    // Highlight
                    particlePaint.setStyle(Paint.Style.FILL);
                    particlePaint.setColor(0x88FFFFFF);
                    canvas.drawCircle(-p.size * 0.3f, -p.size * 0.3f, p.size * 0.25f, particlePaint);
                    break;
                case SWEAT_DROP:
                    drawSweatDrop(canvas, particlePaint, Vector2.zero, p.size);
                    break;
                case SPARKLE:
                    drawSparkle(canvas, particlePaint, Vector2.zero, p.size);
                    break;
                case DUST:
                    particlePaint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(0, 0, p.size, particlePaint);
                    break;
                case ZZZ:
                    particlePaint.setTextSize(p.size);
                    particlePaint.setStyle(Paint.Style.FILL);
                    canvas.drawText("Z", 0, 0, particlePaint);
                    break;
                case ANGER:
                    drawAngerMark(canvas, particlePaint, Vector2.zero, p.size);
                    break;
            }

            canvas.restore();
        }
    }

    // ============== TRAIL SYSTEM ==============

    private void updateTrail(GoosePhysics physics, float deltaTime) {
        // Only add trail points when moving fast
        if (physics.getCurrentSpeed() < SPEED_LINE_THRESHOLD * 0.5f) {
            // Fade out existing points
            Iterator<TrailPoint> it = trailPoints.iterator();
            while (it.hasNext()) {
                TrailPoint tp = it.next();
                tp.alpha -= deltaTime / TRAIL_FADE_TIME;
                if (tp.alpha <= 0) {
                    it.remove();
                }
            }
            return;
        }

        // Add new trail point
        if (Time.time - lastTrailTime >= TRAIL_POINT_INTERVAL) {
            if (trailPoints.size() >= MAX_TRAIL_POINTS) {
                trailPoints.remove(0);
            }
            trailPoints.add(new TrailPoint(physics.getPosition(), Time.time));
            lastTrailTime = Time.time;
        }

        // Update alpha based on age
        for (TrailPoint tp : trailPoints) {
            float age = (float) (Time.time - tp.time);
            tp.alpha = Math.max(0, 1f - (age / TRAIL_FADE_TIME));
        }
    }

    private void renderTrail(Canvas canvas, GoosePhysics physics) {
        if (trailPoints.size() < 2) return;

        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setColor(0xFFFFFFFF);

        for (int i = 1; i < trailPoints.size(); i++) {
            TrailPoint prev = trailPoints.get(i - 1);
            TrailPoint curr = trailPoints.get(i);

            float alpha = Math.min(prev.alpha, curr.alpha);
            trailPaint.setAlpha((int)(alpha * 100));
            trailPaint.setStrokeWidth(8f * alpha);

            canvas.drawLine(prev.position.x, prev.position.y,
                           curr.position.x, curr.position.y, trailPaint);
        }
    }

    private void renderSpeedLines(Canvas canvas, GoosePhysics physics) {
        if (physics.getCurrentSpeed() < SPEED_LINE_THRESHOLD) return;

        Vector2 pos = physics.getPosition();
        float dir = physics.getDirection();
        Vector2 backward = Vector2.GetFromAngleDegrees(dir + 180);

        trailPaint.setColor(0xFFFFFFFF);
        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeWidth(2f);

        float speedFactor = Math.min(1f, physics.getCurrentSpeed() / 300f);
        int lineCount = (int)(3 + speedFactor * 4);

        for (int i = 0; i < lineCount; i++) {
            float offsetAngle = dir + 180 + SamMath.RandomRange(-30, 30);
            Vector2 lineDir = Vector2.GetFromAngleDegrees(offsetAngle);

            float startDist = SamMath.RandomRange(25, 40);
            float lineLength = SamMath.RandomRange(10, 25) * speedFactor;

            Vector2 start = Vector2.add(pos, Vector2.multiply(lineDir, startDist));
            Vector2 end = Vector2.add(start, Vector2.multiply(lineDir, lineLength));

            trailPaint.setAlpha((int)(SamMath.RandomRange(50, 150) * speedFactor));
            canvas.drawLine(start.x, start.y, end.x, end.y, trailPaint);
        }
    }

    // ============== GLOW SYSTEM ==============

    private void updateGlow(GooseAI ai, float deltaTime) {
        glowPhase += GLOW_PULSE_SPEED * deltaTime;
        if (glowPhase > Math.PI * 2) {
            glowPhase -= (float)(Math.PI * 2);
        }

        // Determine glow color and intensity based on state
        GooseTasks.GooseTask task = ai.getCurrentTask();
        PetNeeds.MoodState mood = PetNeeds.get().getMoodState();

        int targetColor = 0;
        float targetIntensity = 0;

        switch (task) {
            case BeingPetted:
                targetColor = 0xFFFF69B4; // Pink
                targetIntensity = 0.8f;
                break;
            case Happy:
            case Playing:
                targetColor = 0xFFFFD700; // Gold
                targetIntensity = 0.6f;
                break;
            case Sleeping:
                targetColor = 0xFF6666FF; // Blue
                targetIntensity = 0.4f;
                break;
            case Sad:
                targetColor = 0xFF4169E1; // Royal blue
                targetIntensity = 0.3f;
                break;
            case Eating:
                targetColor = 0xFF90EE90; // Light green
                targetIntensity = 0.5f;
                break;
            default:
                if (mood == PetNeeds.MoodState.HAPPY) {
                    targetColor = 0xFFFFE4B5; // Moccasin
                    targetIntensity = 0.3f;
                }
                break;
        }

        // Smooth transition
        glowIntensity = SamMath.Lerp(glowIntensity, targetIntensity, deltaTime * 3f);
        currentGlowColor = targetColor;
    }

    private void renderGlow(Canvas canvas, Vector2 position) {
        if (glowIntensity < 0.05f) return;

        float pulse = 1f + (float)Math.sin(glowPhase) * 0.15f;
        float radius = 45f * pulse * glowIntensity;

        // Create radial gradient for glow
        int centerColor = Color.argb(
            (int)(glowIntensity * 80),
            Color.red(currentGlowColor),
            Color.green(currentGlowColor),
            Color.blue(currentGlowColor)
        );
        int edgeColor = Color.argb(0,
            Color.red(currentGlowColor),
            Color.green(currentGlowColor),
            Color.blue(currentGlowColor)
        );

        RadialGradient gradient = new RadialGradient(
            position.x, position.y - 15,
            radius,
            centerColor, edgeColor,
            Shader.TileMode.CLAMP
        );

        glowPaint.setShader(gradient);
        glowPaint.setMaskFilter(null); // Disable blur for gradient

        canvas.drawCircle(position.x, position.y - 15, radius, glowPaint);
        glowPaint.setShader(null);
    }

    // ============== STATUS INDICATORS ==============

    private void renderStatusIndicators(Canvas canvas, Vector2 position) {
        float hunger = PetNeeds.get().hunger;
        float energy = PetNeeds.get().energy;
        float happiness = PetNeeds.get().happiness;

        // Only show indicators when needs are critical
        float iconY = position.y - 70;
        float iconSpacing = 18;
        int iconCount = 0;

        indicatorPaint.setAntiAlias(true);

        // Hunger indicator (when hungry > 70)
        if (hunger > 70) {
            float iconX = position.x + (iconCount - 1) * iconSpacing;
            drawHungerIcon(canvas, iconX, iconY, hunger);
            iconCount++;
        }

        // Energy indicator (when energy < 30)
        if (energy < 30) {
            float iconX = position.x + (iconCount - 1) * iconSpacing;
            drawEnergyIcon(canvas, iconX, iconY, energy);
            iconCount++;
        }

        // Happiness indicator (when happiness < 30)
        if (happiness < 30) {
            float iconX = position.x + (iconCount - 1) * iconSpacing;
            drawHappinessIcon(canvas, iconX, iconY, happiness);
            iconCount++;
        }
    }

    private void drawHungerIcon(Canvas canvas, float x, float y, float hunger) {
        // Pulsing effect when critical
        float pulse = hunger > 85 ? 1f + (float)Math.sin(Time.time * 5) * 0.15f : 1f;
        float size = 8 * pulse;

        // Fork/spoon icon (simplified as plate)
        indicatorPaint.setColor(0xFFFF8C00);
        indicatorPaint.setStyle(Paint.Style.STROKE);
        indicatorPaint.setStrokeWidth(2f);
        canvas.drawCircle(x, y, size, indicatorPaint);

        // Inner circle
        indicatorPaint.setStyle(Paint.Style.FILL);
        indicatorPaint.setColor(0x88FF8C00);
        canvas.drawCircle(x, y, size * 0.5f, indicatorPaint);
    }

    private void drawEnergyIcon(Canvas canvas, float x, float y, float energy) {
        float pulse = energy < 15 ? 1f + (float)Math.sin(Time.time * 5) * 0.15f : 1f;
        float size = 7 * pulse;

        // Lightning bolt
        indicatorPaint.setColor(0xFFFFD700);
        indicatorPaint.setStyle(Paint.Style.FILL);

        Path bolt = new Path();
        bolt.moveTo(x + size * 0.2f, y - size);
        bolt.lineTo(x - size * 0.3f, y);
        bolt.lineTo(x + size * 0.1f, y);
        bolt.lineTo(x - size * 0.2f, y + size);
        bolt.lineTo(x + size * 0.5f, y - size * 0.2f);
        bolt.lineTo(x + size * 0.1f, y - size * 0.2f);
        bolt.close();

        canvas.drawPath(bolt, indicatorPaint);
    }

    private void drawHappinessIcon(Canvas canvas, float x, float y, float happiness) {
        float pulse = happiness < 15 ? 1f + (float)Math.sin(Time.time * 5) * 0.15f : 1f;
        float size = 8 * pulse;

        // Sad face
        indicatorPaint.setColor(0xFF4169E1);
        indicatorPaint.setStyle(Paint.Style.STROKE);
        indicatorPaint.setStrokeWidth(1.5f);

        // Face
        canvas.drawCircle(x, y, size, indicatorPaint);

        // Eyes
        indicatorPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x - size * 0.35f, y - size * 0.2f, size * 0.15f, indicatorPaint);
        canvas.drawCircle(x + size * 0.35f, y - size * 0.2f, size * 0.15f, indicatorPaint);

        // Sad mouth
        indicatorPaint.setStyle(Paint.Style.STROKE);
        RectF mouthRect = new RectF(x - size * 0.4f, y + size * 0.1f, x + size * 0.4f, y + size * 0.6f);
        canvas.drawArc(mouthRect, 200, 140, false, indicatorPaint);
    }

    // ============== BODY RENDERING ==============

    // Feather detail paint
    private Paint featherPaint;

    // Advanced shading paints
    private Paint gradientPaint;
    private Paint rimLightPaint;
    private Paint ambientOcclusionPaint;
    private Paint specularPaint;

    // Cached direction for shading
    private Vector2 lastForward = Vector2.zero;

    private void initFeatherPaint() {
        if (featherPaint == null) {
            featherPaint = new Paint();
            featherPaint.setAntiAlias(true);
        }
    }

    private void initShadingPaints() {
        if (gradientPaint == null) {
            gradientPaint = new Paint();
            gradientPaint.setAntiAlias(true);
        }
        if (rimLightPaint == null) {
            rimLightPaint = new Paint();
            rimLightPaint.setAntiAlias(true);
            rimLightPaint.setMaskFilter(new BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL));
        }
        if (ambientOcclusionPaint == null) {
            ambientOcclusionPaint = new Paint();
            ambientOcclusionPaint.setAntiAlias(true);
            ambientOcclusionPaint.setColor(0x33000000);
        }
        if (specularPaint == null) {
            specularPaint = new Paint();
            specularPaint.setAntiAlias(true);
        }
    }

    /**
     * Render realistic webbed goose feet.
     */
    private void renderFeet(Canvas canvas, GoosePhysics physics) {
        initFeatherPaint();
        renderWebFoot(canvas, physics.getLeftFootPos(), physics.getDirection(), false);
        renderWebFoot(canvas, physics.getRightFootPos(), physics.getDirection(), true);
    }

    /**
     * Render a single webbed foot.
     */
    private void renderWebFoot(Canvas canvas, Vector2 pos, float direction, boolean isRight) {
        Paint footPaint = new Paint();
        footPaint.setColor(footColor);
        footPaint.setAntiAlias(true);
        footPaint.setStyle(Paint.Style.FILL);

        float scale = TheGoose.DrawScale * 0.8f;
        float footAngle = direction + (isRight ? 15f : -15f);

        canvas.save();
        canvas.translate(pos.x, pos.y);
        canvas.rotate(footAngle - 90);

        // Main foot pad
        Path foot = new Path();

        // Three toes with webbing
        float toeLength = 8f * scale;
        float toeWidth = 3f * scale;
        float spread = 25f; // degrees between toes

        // Center toe
        foot.moveTo(0, 0);
        foot.lineTo(-toeWidth, -toeLength);
        foot.lineTo(0, -toeLength * 1.2f);
        foot.lineTo(toeWidth, -toeLength);
        foot.lineTo(0, 0);

        // Left toe with webbing
        float leftAngle = (float)Math.toRadians(-spread);
        float lx = (float)Math.sin(leftAngle) * toeLength;
        float ly = (float)Math.cos(leftAngle) * -toeLength;
        foot.lineTo(lx - toeWidth * 0.5f, ly);
        foot.lineTo(lx, ly * 1.1f);
        foot.lineTo(lx + toeWidth * 0.3f, ly);

        // Webbing between left and center
        foot.quadTo(-toeWidth * 0.5f, -toeLength * 0.6f, 0, 0);

        // Right toe with webbing
        float rightAngle = (float)Math.toRadians(spread);
        float rx = (float)Math.sin(rightAngle) * toeLength;
        float ry = (float)Math.cos(rightAngle) * -toeLength;
        foot.lineTo(rx - toeWidth * 0.3f, ry);
        foot.lineTo(rx, ry * 1.1f);
        foot.lineTo(rx + toeWidth * 0.5f, ry);

        // Webbing between center and right
        foot.quadTo(toeWidth * 0.5f, -toeLength * 0.6f, 0, 0);

        foot.close();

        // Draw foot outline
        Paint outlinePaint = new Paint();
        outlinePaint.setColor(darkenColor(footColor, 0.7f));
        outlinePaint.setAntiAlias(true);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(1.5f * scale);
        canvas.drawPath(foot, outlinePaint);

        // Draw foot fill
        canvas.drawPath(foot, footPaint);

        // Add subtle webbing texture
        Paint webPaint = new Paint();
        webPaint.setColor(darkenColor(footColor, 0.85f));
        webPaint.setAntiAlias(true);
        webPaint.setStyle(Paint.Style.STROKE);
        webPaint.setStrokeWidth(0.5f * scale);

        // Web lines
        canvas.drawLine(0, -toeLength * 0.3f, lx * 0.5f, ly * 0.5f, webPaint);
        canvas.drawLine(0, -toeLength * 0.3f, rx * 0.5f, ry * 0.5f, webPaint);

        canvas.restore();
    }

    private void renderShadow(Canvas canvas, Vector2 position) {
        if (!showShadow) return;

        // More detailed elliptical shadow
        Paint shadowPaint = new Paint();
        shadowPaint.setAntiAlias(true);

        // Create gradient shadow
        RadialGradient shadowGradient = new RadialGradient(
            position.x, position.y,
            Math.max(1f, 25f * TheGoose.DrawScale),
            new int[]{0x44000000, 0x22000000, 0x00000000},
            new float[]{0f, 0.6f, 1f},
            Shader.TileMode.CLAMP
        );
        shadowPaint.setShader(shadowGradient);

        canvas.drawOval(new RectF(
            position.x - 22 * TheGoose.DrawScale,
            position.y - 12 * TheGoose.DrawScale,
            position.x + 22 * TheGoose.DrawScale,
            position.y + 12 * TheGoose.DrawScale
        ), shadowPaint);
    }

    /**
     * Render the goose body with anatomically correct shape.
     */
    private void renderBody(Canvas canvas, GooseRig rig, Vector2 forward) {
        initFeatherPaint();
        float scale = rig.getScale();

        // Calculate perpendicular vector
        Vector2 right = new Vector2(-forward.y, forward.x);

        // ===== TAIL =====
        renderTail(canvas, rig, forward, scale);

        // ===== WINGS (back layer) =====
        renderWings(canvas, rig, forward, scale, true);

        // ===== BODY =====
        renderMainBody(canvas, rig, forward, right, scale);

        // ===== WINGS (front layer) =====
        renderWings(canvas, rig, forward, scale, false);

        // ===== NECK =====
        renderNeck(canvas, rig, forward, scale);

        // ===== HEAD =====
        renderHead(canvas, rig, forward, scale);

        // ===== BEAK =====
        renderBeak(canvas, rig, forward, scale);
    }

    /**
     * Render the main body shape with advanced shading.
     */
    private void renderMainBody(Canvas canvas, GooseRig rig, Vector2 forward, Vector2 right, float scale) {
        initShadingPaints();

        // Create body path for smooth oval shape
        Path bodyPath = new Path();

        // Body ellipse parameters
        float bodyLength = 24f * scale;
        float bodyWidth = 14f * scale;

        // Calculate body points
        Vector2 bodyFront = Vector2.add(rig.bodyCenter, Vector2.multiply(forward, bodyLength * 0.5f));
        Vector2 bodyBack = Vector2.subtract(rig.bodyCenter, Vector2.multiply(forward, bodyLength * 0.5f));
        Vector2 bodyLeft = Vector2.subtract(rig.bodyCenter, Vector2.multiply(right, bodyWidth));
        Vector2 bodyRight = Vector2.add(rig.bodyCenter, Vector2.multiply(right, bodyWidth));

        // Create smooth body shape
        bodyPath.moveTo(bodyFront.x, bodyFront.y);
        bodyPath.quadTo(
            bodyRight.x + forward.x * bodyLength * 0.3f,
            bodyRight.y + forward.y * bodyLength * 0.3f,
            bodyRight.x, bodyRight.y
        );
        bodyPath.quadTo(
            bodyBack.x + right.x * bodyWidth * 0.5f,
            bodyBack.y + right.y * bodyWidth * 0.5f,
            bodyBack.x, bodyBack.y
        );
        bodyPath.quadTo(
            bodyLeft.x - forward.x * bodyLength * 0.3f,
            bodyLeft.y - forward.y * bodyLength * 0.3f,
            bodyLeft.x, bodyLeft.y
        );
        bodyPath.quadTo(
            bodyFront.x - right.x * bodyWidth * 0.5f,
            bodyFront.y - right.y * bodyWidth * 0.5f,
            bodyFront.x, bodyFront.y
        );
        bodyPath.close();

        // === PHASE 1: Body gradient for 3D volume ===
        // Create linear gradient from front (bright) to back (darker)
        LinearGradient bodyGradient = new LinearGradient(
            bodyFront.x, bodyFront.y,
            bodyBack.x, bodyBack.y,
            new int[]{bodyColor, darkenColor(bodyColor, 0.92f), darkenColor(bodyColor, 0.85f)},
            new float[]{0f, 0.6f, 1f},
            Shader.TileMode.CLAMP
        );

        // Create radial gradient for volume (center lighter, edges darker)
        RadialGradient volumeGradient = new RadialGradient(
            rig.bodyCenter.x, rig.bodyCenter.y - 3f * scale,
            bodyLength * 0.7f,
            new int[]{lightenColor(bodyColor, 1.05f), bodyColor, darkenColor(bodyColor, 0.9f)},
            new float[]{0f, 0.5f, 1f},
            Shader.TileMode.CLAMP
        );

        // Combine gradients using compose shader
        ComposeShader combinedShader = new ComposeShader(
            bodyGradient, volumeGradient, android.graphics.PorterDuff.Mode.MULTIPLY
        );

        // Draw body fill with gradient
        gradientPaint.setStyle(Paint.Style.FILL);
        gradientPaint.setShader(combinedShader);
        canvas.drawPath(bodyPath, gradientPaint);
        gradientPaint.setShader(null);

        // === PHASE 1: Rim lighting for 3D effect ===
        renderRimLight(canvas, bodyPath, rig.bodyCenter, bodyLength, scale);

        // Draw body outline
        drawingPen.setColor(outlineColor);
        drawingPen.setStyle(Paint.Style.STROKE);
        drawingPen.setStrokeWidth(3f * scale);
        canvas.drawPath(bodyPath, drawingPen);

        // Add feather texture
        renderFeatherTexture(canvas, rig.bodyCenter, forward, bodyLength * 0.8f, bodyWidth * 0.7f, scale);

        // Underbody (belly) - slightly darker
        Paint bellyPaint = new Paint();
        bellyPaint.setColor(darkenColor(bodyColor, 0.95f));
        bellyPaint.setAntiAlias(true);

        Path bellyPath = new Path();
        Vector2 bellyCenter = Vector2.add(rig.underbodyCenter, Vector2.multiply(new Vector2(0, 1), 2f * scale));
        float bellyLength = 16f * scale;
        float bellyWidth = 10f * scale;

        Vector2 bellyFront = Vector2.add(bellyCenter, Vector2.multiply(forward, bellyLength * 0.4f));
        Vector2 bellyBack = Vector2.subtract(bellyCenter, Vector2.multiply(forward, bellyLength * 0.5f));

        bellyPath.moveTo(bellyFront.x, bellyFront.y);
        bellyPath.quadTo(
            bellyCenter.x + right.x * bellyWidth,
            bellyCenter.y + right.y * bellyWidth + 3f * scale,
            bellyBack.x, bellyBack.y
        );
        bellyPath.quadTo(
            bellyCenter.x - right.x * bellyWidth,
            bellyCenter.y - right.y * bellyWidth + 3f * scale,
            bellyFront.x, bellyFront.y
        );

        canvas.drawPath(bellyPath, bellyPaint);
    }

    /**
     * Render the neck with smooth curves and gradient shading.
     */
    private void renderNeck(Canvas canvas, GooseRig rig, Vector2 forward, float scale) {
        initShadingPaints();

        // Create curved neck path
        Path neckPath = new Path();

        float neckWidth = 7f * scale;
        Vector2 right = new Vector2(-forward.y, forward.x);

        // Control points for bezier curve
        Vector2 neckMid = Vector2.Lerp(rig.neckBase, rig.neckHeadPoint, 0.5f);
        Vector2 neckControl = Vector2.add(neckMid, Vector2.multiply(forward, 5f * scale));

        // Left side of neck
        Vector2 neckBaseLeft = Vector2.subtract(rig.neckBase, Vector2.multiply(right, neckWidth));
        Vector2 neckHeadLeft = Vector2.subtract(rig.neckHeadPoint, Vector2.multiply(right, neckWidth * 0.8f));

        // Right side of neck
        Vector2 neckBaseRight = Vector2.add(rig.neckBase, Vector2.multiply(right, neckWidth));
        Vector2 neckHeadRight = Vector2.add(rig.neckHeadPoint, Vector2.multiply(right, neckWidth * 0.8f));

        neckPath.moveTo(neckBaseLeft.x, neckBaseLeft.y);
        neckPath.quadTo(neckControl.x - right.x * neckWidth, neckControl.y - right.y * neckWidth,
                       neckHeadLeft.x, neckHeadLeft.y);
        neckPath.lineTo(neckHeadRight.x, neckHeadRight.y);
        neckPath.quadTo(neckControl.x + right.x * neckWidth, neckControl.y + right.y * neckWidth,
                       neckBaseRight.x, neckBaseRight.y);
        neckPath.close();

        // === Neck gradient for 3D volume ===
        LinearGradient neckGradient = new LinearGradient(
            neckBaseLeft.x, neckBaseLeft.y,
            neckBaseRight.x, neckBaseRight.y,
            new int[]{darkenColor(bodyColor, 0.88f), bodyColor, lightenColor(bodyColor, 1.02f), bodyColor, darkenColor(bodyColor, 0.88f)},
            new float[]{0f, 0.25f, 0.5f, 0.75f, 1f},
            Shader.TileMode.CLAMP
        );

        // Draw neck fill with gradient
        gradientPaint.setStyle(Paint.Style.FILL);
        gradientPaint.setShader(neckGradient);
        canvas.drawPath(neckPath, gradientPaint);
        gradientPaint.setShader(null);

        // Rim light on neck
        renderRimLight(canvas, neckPath, neckMid, neckWidth * 2, scale);

        // Ambient occlusion where neck meets body
        renderAmbientOcclusion(canvas, rig.neckBase, neckWidth, scale);

        // Draw neck outline
        drawingPen.setColor(outlineColor);
        drawingPen.setStyle(Paint.Style.STROKE);
        drawingPen.setStrokeWidth(2f * scale);
        canvas.drawPath(neckPath, drawingPen);
    }

    /**
     * Render the head with proper goose shape and gradient shading.
     */
    private void renderHead(Canvas canvas, GooseRig rig, Vector2 forward, float scale) {
        initShadingPaints();

        // Head is more oval/egg shaped
        Path headPath = new Path();

        float headLength = 12f * scale;
        float headWidth = 8f * scale;
        Vector2 right = new Vector2(-forward.y, forward.x);

        Vector2 headCenter = Vector2.Lerp(rig.neckHeadPoint, rig.head2EndPoint, 0.4f);
        Vector2 headFront = Vector2.add(headCenter, Vector2.multiply(forward, headLength * 0.6f));
        Vector2 headBack = Vector2.subtract(headCenter, Vector2.multiply(forward, headLength * 0.4f));

        // Create egg shape
        headPath.moveTo(headFront.x, headFront.y);
        headPath.quadTo(
            headCenter.x + right.x * headWidth,
            headCenter.y + right.y * headWidth,
            headBack.x, headBack.y
        );
        headPath.quadTo(
            headCenter.x - right.x * headWidth,
            headCenter.y - right.y * headWidth,
            headFront.x, headFront.y
        );

        // === Head gradient for 3D volume ===
        RadialGradient headGradient = new RadialGradient(
            headCenter.x - forward.x * headLength * 0.1f,
            headCenter.y - forward.y * headLength * 0.1f - 2f * scale,
            headLength * 0.8f,
            new int[]{lightenColor(bodyColor, 1.05f), bodyColor, darkenColor(bodyColor, 0.9f)},
            new float[]{0f, 0.6f, 1f},
            Shader.TileMode.CLAMP
        );

        // Draw head fill with gradient
        gradientPaint.setStyle(Paint.Style.FILL);
        gradientPaint.setShader(headGradient);
        canvas.drawPath(headPath, gradientPaint);
        gradientPaint.setShader(null);

        // Rim light on head
        renderRimLight(canvas, headPath, headCenter, headLength, scale);

        // Specular highlight on forehead
        Vector2 specPos = Vector2.add(headCenter, Vector2.multiply(forward, headLength * 0.2f));
        specPos = Vector2.add(specPos, new Vector2(0, -2f * scale));
        renderSpecularHighlight(canvas, specPos, 3f, 0.4f, scale);

        // Draw head outline
        drawingPen.setColor(outlineColor);
        drawingPen.setStyle(Paint.Style.STROKE);
        drawingPen.setStrokeWidth(2f * scale);
        canvas.drawPath(headPath, drawingPen);

        // Dynamic cheek blush based on happiness
        renderDynamicBlush(canvas, rig, headCenter, right, headWidth, scale);
    }

    /**
     * Render dynamic blush effect based on happiness level.
     */
    private void renderDynamicBlush(Canvas canvas, GooseRig rig, Vector2 headCenter,
                                     Vector2 right, float headWidth, float scale) {
        float happiness = PetNeeds.get().happiness;
        if (happiness < 50) return;

        // Calculate blush intensity (0 to 1)
        float intensity = (happiness - 50) / 50f;

        // Pulse effect when very happy
        if (happiness > 80) {
            float pulse = 1f + (float)Math.sin(Time.time * 4f) * 0.2f;
            intensity *= pulse;
        }

        // Color transitions from light pink to deeper pink
        int blushAlpha = (int)(intensity * 80);
        int blushRed = 255;
        int blushGreen = (int)(105 + (1 - intensity) * 50);
        int blushBlue = (int)(180 - intensity * 50);
        int blushColor = Color.argb(blushAlpha, blushRed, blushGreen, blushBlue);

        Paint blushPaint = new Paint();
        blushPaint.setAntiAlias(true);

        // Create radial gradient for soft blush
        float blushSize = 4f * scale * (0.8f + intensity * 0.4f);

        Vector2 leftCheek = Vector2.subtract(headCenter, Vector2.multiply(right, headWidth * 0.55f));
        Vector2 rightCheek = Vector2.add(headCenter, Vector2.multiply(right, headWidth * 0.55f));

        // Left cheek blush with gradient
        RadialGradient leftBlush = new RadialGradient(
            leftCheek.x, leftCheek.y, blushSize,
            blushColor, Color.argb(0, blushRed, blushGreen, blushBlue),
            Shader.TileMode.CLAMP
        );
        blushPaint.setShader(leftBlush);
        canvas.drawCircle(leftCheek.x, leftCheek.y, blushSize, blushPaint);

        // Right cheek blush with gradient
        RadialGradient rightBlush = new RadialGradient(
            rightCheek.x, rightCheek.y, blushSize,
            blushColor, Color.argb(0, blushRed, blushGreen, blushBlue),
            Shader.TileMode.CLAMP
        );
        blushPaint.setShader(rightBlush);
        canvas.drawCircle(rightCheek.x, rightCheek.y, blushSize, blushPaint);

        blushPaint.setShader(null);
    }

    /**
     * Render a realistic animated goose beak.
     */
    private void renderBeak(Canvas canvas, GooseRig rig, Vector2 forward, float scale) {
        initShadingPaints();

        float beakLength = 10f * scale;
        float beakHeight = 4f * scale;
        Vector2 up = new Vector2(0, -1);

        Vector2 beakStart = rig.head2EndPoint;

        // Get beak open amount from rig
        float openAmount = rig.getBeakOpenAmount();
        float openAngle = openAmount * 15f; // Max 15 degrees open

        // Calculate beak tip (moves slightly based on opening)
        Vector2 beakTip = Vector2.add(beakStart, Vector2.multiply(forward, beakLength * (1f - openAmount * 0.05f)));

        // === UPPER BEAK ===
        Path upperBeakPath = new Path();
        Vector2 upperStart = Vector2.add(beakStart, Vector2.multiply(up, beakHeight * 0.3f));
        Vector2 upperMid = Vector2.add(
            Vector2.add(beakStart, Vector2.multiply(forward, beakLength * 0.6f)),
            Vector2.multiply(up, beakHeight * 0.5f)
        );
        Vector2 upperTip = beakTip;

        upperBeakPath.moveTo(beakStart.x, beakStart.y);
        upperBeakPath.lineTo(upperStart.x, upperStart.y);
        upperBeakPath.quadTo(upperMid.x, upperMid.y, upperTip.x, upperTip.y);
        upperBeakPath.close();

        // Upper beak gradient (wet/shiny effect)
        LinearGradient upperGradient = new LinearGradient(
            upperStart.x, upperStart.y,
            upperTip.x, upperTip.y,
            new int[]{lightenColor(mouthColor, 1.1f), mouthColor, darkenColor(mouthColor, 0.85f)},
            new float[]{0f, 0.4f, 1f},
            Shader.TileMode.CLAMP
        );

        Paint beakFill = new Paint();
        beakFill.setAntiAlias(true);
        beakFill.setStyle(Paint.Style.FILL);
        beakFill.setShader(upperGradient);
        canvas.drawPath(upperBeakPath, beakFill);
        beakFill.setShader(null);

        // === LOWER BEAK (animated) ===
        Path lowerBeakPath = new Path();

        // Lower beak rotates down when mouth opens
        float lowerAngleRad = (float)Math.toRadians(openAngle);
        Vector2 lowerDir = new Vector2(
            forward.x * (float)Math.cos(lowerAngleRad) - up.x * (float)Math.sin(lowerAngleRad),
            forward.y * (float)Math.cos(lowerAngleRad) - up.y * (float)Math.sin(lowerAngleRad)
        );

        Vector2 lowerStart = Vector2.subtract(beakStart, Vector2.multiply(up, beakHeight * 0.15f));
        Vector2 lowerTip = Vector2.add(lowerStart, Vector2.multiply(lowerDir, beakLength * 0.85f));
        Vector2 lowerMid = Vector2.add(
            lowerStart,
            Vector2.multiply(lowerDir, beakLength * 0.5f)
        );
        lowerMid = Vector2.subtract(lowerMid, Vector2.multiply(up, beakHeight * 0.2f));

        lowerBeakPath.moveTo(beakStart.x, beakStart.y);
        lowerBeakPath.lineTo(lowerStart.x, lowerStart.y);
        lowerBeakPath.quadTo(lowerMid.x, lowerMid.y, lowerTip.x, lowerTip.y);
        lowerBeakPath.close();

        // Lower beak gradient
        LinearGradient lowerGradient = new LinearGradient(
            lowerStart.x, lowerStart.y,
            lowerTip.x, lowerTip.y,
            new int[]{mouthColor, darkenColor(mouthColor, 0.9f)},
            null,
            Shader.TileMode.CLAMP
        );
        beakFill.setShader(lowerGradient);
        canvas.drawPath(lowerBeakPath, beakFill);
        beakFill.setShader(null);

        // Beak outlines
        Paint beakOutline = new Paint();
        beakOutline.setColor(darkenColor(mouthColor, 0.65f));
        beakOutline.setAntiAlias(true);
        beakOutline.setStyle(Paint.Style.STROKE);
        beakOutline.setStrokeWidth(1.5f * scale);
        canvas.drawPath(upperBeakPath, beakOutline);
        canvas.drawPath(lowerBeakPath, beakOutline);

        // Nostril
        Paint nostrilPaint = new Paint();
        nostrilPaint.setColor(darkenColor(mouthColor, 0.5f));
        nostrilPaint.setAntiAlias(true);
        Vector2 nostrilPos = Vector2.add(beakStart, Vector2.multiply(forward, beakLength * 0.35f));
        nostrilPos = Vector2.add(nostrilPos, Vector2.multiply(up, beakHeight * 0.15f));
        canvas.drawOval(new RectF(
            nostrilPos.x - 1.2f * scale,
            nostrilPos.y - 0.8f * scale,
            nostrilPos.x + 1.2f * scale,
            nostrilPos.y + 0.8f * scale
        ), nostrilPaint);

        // Specular highlight on beak tip (wet look)
        renderSpecularHighlight(canvas,
            Vector2.add(beakStart, Vector2.multiply(forward, beakLength * 0.7f)),
            1.5f, 0.5f, scale);

        // Mouth interior when open
        if (openAmount > 0.1f) {
            Paint mouthInterior = new Paint();
            mouthInterior.setColor(0xFF8B0000); // Dark red
            mouthInterior.setAntiAlias(true);

            Path mouthPath = new Path();
            mouthPath.moveTo(beakStart.x, beakStart.y);
            float mouthDepth = beakLength * 0.4f * openAmount;
            Vector2 mouthBack = Vector2.add(beakStart, Vector2.multiply(forward, mouthDepth));
            mouthPath.lineTo(upperTip.x - forward.x * beakLength * 0.3f, upperTip.y - forward.y * beakLength * 0.3f);
            mouthPath.lineTo(lowerTip.x - forward.x * beakLength * 0.3f, lowerTip.y - forward.y * beakLength * 0.3f);
            mouthPath.close();
            canvas.drawPath(mouthPath, mouthInterior);

            // Tongue hint
            if (openAmount > 0.2f) {
                Paint tonguePaint = new Paint();
                tonguePaint.setColor(0xFFFF6B6B);
                tonguePaint.setAntiAlias(true);
                Vector2 tonguePos = Vector2.add(beakStart, Vector2.multiply(forward, beakLength * 0.2f));
                canvas.drawOval(new RectF(
                    tonguePos.x - 2f * scale,
                    tonguePos.y - 1f * scale,
                    tonguePos.x + 2f * scale,
                    tonguePos.y + 1.5f * scale
                ), tonguePaint);
            }
        }
    }

    /**
     * Render wings with animation and detailed feathers.
     */
    private void renderWings(Canvas canvas, GooseRig rig, Vector2 forward, float scale, boolean backLayer) {
        float wingAngle = rig.getWingAngle();
        Vector2 right = new Vector2(-forward.y, forward.x);

        // Apply wing lag from secondary motion
        Vector2 wingLag = rig.getWingLag();

        // Only render one wing per layer (for 3D effect)
        if (backLayer) {
            // Back wing (farther from viewer)
            Vector2 adjustedPos = Vector2.add(rig.leftWingPos, Vector2.multiply(wingLag, 0.3f));
            renderDetailedWing(canvas, adjustedPos, forward, right, scale, wingAngle, true);
        } else {
            // Front wing (closer to viewer)
            Vector2 adjustedPos = Vector2.add(rig.rightWingPos, Vector2.multiply(wingLag, 0.3f));
            renderDetailedWing(canvas, adjustedPos, forward, right, scale, -wingAngle, false);
        }
    }

    /**
     * Render a detailed wing with individual feathers.
     */
    private void renderDetailedWing(Canvas canvas, Vector2 wingPos, Vector2 forward, Vector2 right,
                                     float scale, float angle, boolean isBack) {
        initShadingPaints();

        float wingLength = 18f * scale;
        float wingWidth = 8f * scale;

        // Apply wing angle
        float angleRad = (float)Math.toRadians(angle);
        Vector2 wingDir = new Vector2(
            right.x * (float)Math.cos(angleRad) - (isBack ? -1 : 1) * 0.3f * (float)Math.sin(angleRad),
            right.y * (float)Math.cos(angleRad) + 0.7f * (float)Math.sin(angleRad)
        );

        Vector2 wingTip = Vector2.add(wingPos, Vector2.multiply(wingDir, wingLength));
        Vector2 wingBack = Vector2.subtract(wingPos, Vector2.multiply(forward, wingWidth));

        // Wing color with gradient based on position
        int wingColor = isBack ? darkenColor(bodyColor, 0.88f) : bodyColor;

        // === Render individual primary feathers (8-10 feathers) ===
        int featherCount = 8;
        Paint featherPaint = new Paint();
        featherPaint.setAntiAlias(true);

        for (int i = featherCount - 1; i >= 0; i--) {
            float t = i / (float)(featherCount - 1);

            // Calculate feather position along wing
            Vector2 featherBase = Vector2.Lerp(wingPos, wingBack, t * 0.8f);
            Vector2 featherTip = Vector2.Lerp(wingPos, wingTip, 0.3f + t * 0.7f);

            // Feather direction
            Vector2 featherDir = Vector2.subtract(featherTip, featherBase);
            float featherLen = Vector2.Distance(featherBase, featherTip);
            featherDir = Vector2.Normalize(featherDir);

            // Create feather path with barbs
            Path featherPath = new Path();
            float featherWidth = (3f + (1 - t) * 3f) * scale;

            // Perpendicular for feather width
            Vector2 featherPerp = new Vector2(-featherDir.y, featherDir.x);

            // Feather shape (slightly curved)
            Vector2 leftBase = Vector2.subtract(featherBase, Vector2.multiply(featherPerp, featherWidth * 0.3f));
            Vector2 rightBase = Vector2.add(featherBase, Vector2.multiply(featherPerp, featherWidth * 0.3f));
            Vector2 leftMid = Vector2.subtract(
                Vector2.add(featherBase, Vector2.multiply(featherDir, featherLen * 0.5f)),
                Vector2.multiply(featherPerp, featherWidth * 0.5f)
            );
            Vector2 rightMid = Vector2.add(
                Vector2.add(featherBase, Vector2.multiply(featherDir, featherLen * 0.5f)),
                Vector2.multiply(featherPerp, featherWidth * 0.5f)
            );

            featherPath.moveTo(leftBase.x, leftBase.y);
            featherPath.quadTo(leftMid.x, leftMid.y, featherTip.x, featherTip.y);
            featherPath.quadTo(rightMid.x, rightMid.y, rightBase.x, rightBase.y);
            featherPath.close();

            // Gradient for each feather (lighter at base, darker at tip)
            LinearGradient featherGradient = new LinearGradient(
                featherBase.x, featherBase.y,
                featherTip.x, featherTip.y,
                new int[]{lightenColor(wingColor, 1.02f), wingColor, darkenColor(wingColor, 0.92f)},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
            );

            // Draw feather fill with gradient
            featherPaint.setStyle(Paint.Style.FILL);
            featherPaint.setShader(featherGradient);
            canvas.drawPath(featherPath, featherPaint);
            featherPaint.setShader(null);

            // Draw feather shaft (rachis)
            featherPaint.setStyle(Paint.Style.STROKE);
            featherPaint.setStrokeWidth(0.8f * scale);
            featherPaint.setColor(darkenColor(wingColor, 0.75f));
            canvas.drawLine(featherBase.x, featherBase.y, featherTip.x, featherTip.y, featherPaint);

            // Draw barb lines (subtle texture)
            if (i % 2 == 0) {
                featherPaint.setColor(darkenColor(wingColor, 0.88f));
                featherPaint.setStrokeWidth(0.4f * scale);
                for (int b = 1; b <= 3; b++) {
                    float bt = b / 4f;
                    Vector2 barbStart = Vector2.Lerp(featherBase, featherTip, bt);
                    Vector2 barbEndL = Vector2.subtract(barbStart, Vector2.multiply(featherPerp, featherWidth * 0.4f * (1 - bt)));
                    Vector2 barbEndR = Vector2.add(barbStart, Vector2.multiply(featherPerp, featherWidth * 0.4f * (1 - bt)));
                    canvas.drawLine(barbStart.x, barbStart.y, barbEndL.x, barbEndL.y, featherPaint);
                    canvas.drawLine(barbStart.x, barbStart.y, barbEndR.x, barbEndR.y, featherPaint);
                }
            }
        }

        // === Wing covert feathers (smaller, covering base) ===
        Paint covertPaint = new Paint();
        covertPaint.setAntiAlias(true);
        covertPaint.setColor(wingColor);
        covertPaint.setStyle(Paint.Style.FILL);

        Path covertPath = new Path();
        covertPath.moveTo(wingPos.x, wingPos.y);
        covertPath.quadTo(
            wingPos.x + wingDir.x * wingLength * 0.3f - forward.x * wingWidth * 0.2f,
            wingPos.y + wingDir.y * wingLength * 0.3f - forward.y * wingWidth * 0.2f,
            wingPos.x + wingDir.x * wingLength * 0.4f,
            wingPos.y + wingDir.y * wingLength * 0.4f
        );
        covertPath.quadTo(
            wingBack.x + wingDir.x * wingLength * 0.2f,
            wingBack.y + wingDir.y * wingLength * 0.2f,
            wingBack.x, wingBack.y
        );
        covertPath.close();

        // Gradient for covert
        RadialGradient covertGradient = new RadialGradient(
            wingPos.x, wingPos.y,
            wingLength * 0.5f,
            new int[]{lightenColor(wingColor, 1.03f), wingColor},
            null,
            Shader.TileMode.CLAMP
        );
        covertPaint.setShader(covertGradient);
        canvas.drawPath(covertPath, covertPaint);
        covertPaint.setShader(null);

        // Wing outline
        Paint wingOutline = new Paint();
        wingOutline.setColor(outlineColor);
        wingOutline.setAntiAlias(true);
        wingOutline.setStyle(Paint.Style.STROKE);
        wingOutline.setStrokeWidth(1.5f * scale);
        canvas.drawPath(covertPath, wingOutline);
    }

    /**
     * Render detailed tail feathers with gradients and structure.
     */
    private void renderTail(Canvas canvas, GooseRig rig, Vector2 forward, float scale) {
        initShadingPaints();

        float tailLength = 14f * scale;

        Vector2 right = new Vector2(-forward.y, forward.x);
        float wagAngle = rig.getTailWagAngle();

        // Apply tail lag from secondary motion
        Vector2 tailLag = rig.getTailLag();

        // Tail direction with wag and lag
        Vector2 tailDir = Vector2.add(
            Vector2.multiply(forward, -1f),
            Vector2.multiply(right, wagAngle)
        );
        tailDir = Vector2.Normalize(tailDir);

        // Adjusted tail position with lag
        Vector2 tailBase = Vector2.add(rig.tailPos, Vector2.multiply(tailLag, 0.2f));

        // Create tail feather fan shape - 7 feathers for more detail
        int featherCount = 7;
        float spreadAngle = 35f;

        Paint featherPaint = new Paint();
        featherPaint.setAntiAlias(true);

        // Render feathers from outside to center
        for (int i = 0; i < featherCount; i++) {
            // Angle from center (0 is center feather)
            float distFromCenter = Math.abs(i - featherCount / 2f);
            float featherAngle = (i - featherCount / 2f) * (spreadAngle / (featherCount - 1));
            float angleRad = (float)Math.toRadians(featherAngle);

            // Rotate direction
            Vector2 featherDir = new Vector2(
                tailDir.x * (float)Math.cos(angleRad) - tailDir.y * (float)Math.sin(angleRad),
                tailDir.x * (float)Math.sin(angleRad) + tailDir.y * (float)Math.cos(angleRad)
            );

            // Feather length varies (center feathers longer)
            float thisLength = tailLength * (1f - distFromCenter * 0.08f);
            Vector2 featherTip = Vector2.add(tailBase, Vector2.multiply(featherDir, thisLength));

            // Perpendicular for feather width
            Vector2 featherPerp = new Vector2(-featherDir.y, featherDir.x);
            float featherWidth = (2.5f - distFromCenter * 0.2f) * scale;

            // Create feather path
            Path featherPath = new Path();

            // Base of feather
            Vector2 baseLeft = Vector2.subtract(tailBase, Vector2.multiply(featherPerp, featherWidth * 0.3f));
            Vector2 baseRight = Vector2.add(tailBase, Vector2.multiply(featherPerp, featherWidth * 0.3f));

            // Mid point (widest part)
            Vector2 midPoint = Vector2.add(tailBase, Vector2.multiply(featherDir, thisLength * 0.4f));
            Vector2 midLeft = Vector2.subtract(midPoint, Vector2.multiply(featherPerp, featherWidth * 0.6f));
            Vector2 midRight = Vector2.add(midPoint, Vector2.multiply(featherPerp, featherWidth * 0.6f));

            // Build feather shape
            featherPath.moveTo(baseLeft.x, baseLeft.y);
            featherPath.quadTo(midLeft.x, midLeft.y, featherTip.x, featherTip.y);
            featherPath.quadTo(midRight.x, midRight.y, baseRight.x, baseRight.y);
            featherPath.close();

            // Gradient for each tail feather
            LinearGradient tailGradient = new LinearGradient(
                tailBase.x, tailBase.y,
                featherTip.x, featherTip.y,
                new int[]{bodyColor, darkenColor(bodyColor, 0.95f), darkenColor(bodyColor, 0.88f)},
                new float[]{0f, 0.6f, 1f},
                Shader.TileMode.CLAMP
            );

            // Draw feather fill
            featherPaint.setStyle(Paint.Style.FILL);
            featherPaint.setShader(tailGradient);
            canvas.drawPath(featherPath, featherPaint);
            featherPaint.setShader(null);

            // Draw feather shaft (rachis)
            featherPaint.setStyle(Paint.Style.STROKE);
            featherPaint.setStrokeWidth(1f * scale);
            featherPaint.setColor(darkenColor(bodyColor, 0.75f));
            canvas.drawLine(tailBase.x, tailBase.y, featherTip.x, featherTip.y, featherPaint);

            // Draw subtle barb lines
            featherPaint.setColor(darkenColor(bodyColor, 0.9f));
            featherPaint.setStrokeWidth(0.5f * scale);
            for (int b = 1; b <= 4; b++) {
                float bt = b / 5f;
                Vector2 barbPoint = Vector2.Lerp(tailBase, featherTip, bt);
                float barbLen = featherWidth * 0.5f * (1 - bt * 0.5f);
                Vector2 barbL = Vector2.subtract(barbPoint, Vector2.multiply(featherPerp, barbLen));
                Vector2 barbR = Vector2.add(barbPoint, Vector2.multiply(featherPerp, barbLen));
                canvas.drawLine(barbPoint.x, barbPoint.y, barbL.x, barbL.y, featherPaint);
                canvas.drawLine(barbPoint.x, barbPoint.y, barbR.x, barbR.y, featherPaint);
            }

            // Subtle dark stripe following curve on outer feathers
            if (distFromCenter > 1) {
                featherPaint.setColor(darkenColor(bodyColor, 0.85f));
                featherPaint.setStrokeWidth(1.2f * scale);
                Vector2 stripeStart = Vector2.Lerp(tailBase, featherTip, 0.5f);
                Vector2 stripeEnd = Vector2.Lerp(tailBase, featherTip, 0.9f);
                Vector2 stripeOff = Vector2.multiply(featherPerp, featherWidth * 0.3f * (i < featherCount / 2 ? -1 : 1));
                canvas.drawLine(
                    stripeStart.x + stripeOff.x, stripeStart.y + stripeOff.y,
                    stripeEnd.x + stripeOff.x, stripeEnd.y + stripeOff.y,
                    featherPaint
                );
            }
        }
    }

    /**
     * Render subtle feather texture.
     */
    private void renderFeatherTexture(Canvas canvas, Vector2 center, Vector2 forward,
                                       float length, float width, float scale) {
        Paint texturePaint = new Paint();
        texturePaint.setColor(darkenColor(bodyColor, 0.92f));
        texturePaint.setAntiAlias(true);
        texturePaint.setStrokeWidth(0.5f * scale);
        texturePaint.setStyle(Paint.Style.STROKE);

        Vector2 right = new Vector2(-forward.y, forward.x);

        // Draw subtle curved lines for feather texture
        int lineCount = 4;
        for (int i = 0; i < lineCount; i++) {
            float t = (i + 1f) / (lineCount + 1f);
            Vector2 lineStart = Vector2.add(center,
                Vector2.add(
                    Vector2.multiply(forward, length * (0.3f - t * 0.5f)),
                    Vector2.multiply(right, -width * 0.6f)
                )
            );
            Vector2 lineEnd = Vector2.add(center,
                Vector2.add(
                    Vector2.multiply(forward, length * (0.3f - t * 0.5f)),
                    Vector2.multiply(right, width * 0.6f)
                )
            );

            Path featherLine = new Path();
            featherLine.moveTo(lineStart.x, lineStart.y);
            featherLine.quadTo(center.x, center.y - 2f * scale * t, lineEnd.x, lineEnd.y);
            canvas.drawPath(featherLine, texturePaint);
        }
    }

    /**
     * Helper to darken a color.
     */
    private int darkenColor(int color, float factor) {
        int a = Color.alpha(color);
        int r = (int)(Color.red(color) * factor);
        int g = (int)(Color.green(color) * factor);
        int b = (int)(Color.blue(color) * factor);
        return Color.argb(a, Math.min(255, r), Math.min(255, g), Math.min(255, b));
    }

    /**
     * Helper to lighten a color.
     */
    private int lightenColor(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.min(255, (int)(Color.red(color) * factor));
        int g = Math.min(255, (int)(Color.green(color) * factor));
        int b = Math.min(255, (int)(Color.blue(color) * factor));
        return Color.argb(a, r, g, b);
    }

    /**
     * Render rim lighting effect around a path for 3D depth.
     */
    private void renderRimLight(Canvas canvas, Path path, Vector2 center, float size, float scale) {
        // Create rim light paint with blur
        Paint rimPaint = new Paint();
        rimPaint.setAntiAlias(true);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(4f * scale);
        rimPaint.setColor(0x66FFFFFF); // White with 40% alpha
        rimPaint.setMaskFilter(new BlurMaskFilter(8f * scale, BlurMaskFilter.Blur.NORMAL));

        // Draw rim light slightly offset for top-light effect
        canvas.save();
        canvas.translate(0, -2f * scale);
        canvas.drawPath(path, rimPaint);
        canvas.restore();
    }

    /**
     * Render ambient occlusion (contact shadows) where parts meet.
     */
    private void renderAmbientOcclusion(Canvas canvas, Vector2 position, float radius, float scale) {
        ambientOcclusionPaint.setColor(0x22000000); // Very subtle black
        ambientOcclusionPaint.setMaskFilter(new BlurMaskFilter(4f * scale, BlurMaskFilter.Blur.NORMAL));
        canvas.drawCircle(position.x, position.y + 2f * scale, radius * 0.8f, ambientOcclusionPaint);
        ambientOcclusionPaint.setMaskFilter(null);
    }

    /**
     * Render specular highlight on a surface.
     */
    private void renderSpecularHighlight(Canvas canvas, Vector2 position, float size, float intensity, float scale) {
        int alpha = (int)(255 * intensity * 0.6f);
        specularPaint.setColor(Color.argb(alpha, 255, 255, 255));

        // Main specular
        RadialGradient specGradient = new RadialGradient(
            position.x, position.y,
            size * scale,
            new int[]{Color.argb(alpha, 255, 255, 255), Color.argb(0, 255, 255, 255)},
            null,
            Shader.TileMode.CLAMP
        );
        specularPaint.setShader(specGradient);
        canvas.drawCircle(position.x, position.y, size * scale, specularPaint);
        specularPaint.setShader(null);
    }

    /**
     * Render dynamic specular highlights that respond to movement.
     */
    private void renderDynamicSpeculars(Canvas canvas, GooseRig rig, GoosePhysics physics, Vector2 forward) {
        initShadingPaints();

        float scale = rig.getScale();
        float speed = physics.getCurrentSpeed();

        // Intensity increases with movement
        float baseIntensity = 0.25f;
        float movementBonus = Math.min(0.4f, speed / 300f);
        float intensity = baseIntensity + movementBonus;

        // Specular position shifts opposite to movement direction
        Vector2 velocity = new Vector2(
            physics.getPosition().x - rig.bodyCenter.x,
            physics.getPosition().y - rig.bodyCenter.y
        );

        // Chest specular
        Vector2 chestSpec = Vector2.add(
            rig.bodyCenter,
            Vector2.multiply(forward, 8f * scale)
        );
        chestSpec = Vector2.add(chestSpec, new Vector2(0, -5f * scale));

        // Shift based on movement
        if (speed > 10f) {
            Vector2 shiftDir = Vector2.Normalize(Vector2.multiply(velocity, -1f));
            chestSpec = Vector2.add(chestSpec, Vector2.multiply(shiftDir, 3f * scale));
        }

        // Draw chest specular
        renderSpecularHighlight(canvas, chestSpec, 5f, intensity, scale);

        // Neck specular (smaller)
        Vector2 neckMid = Vector2.Lerp(rig.neckBase, rig.neckHeadPoint, 0.5f);
        renderSpecularHighlight(canvas, neckMid, 2.5f, intensity * 0.7f, scale);

        // Pulse effect when moving fast
        if (speed > 100f) {
            float pulse = (float)Math.sin(Time.time * 8f) * 0.15f;
            float pulseIntensity = intensity + pulse;

            // Extra sparkle at high speed
            Vector2 sparklePos = Vector2.add(
                rig.bodyCenter,
                new Vector2(
                    (float)Math.sin(Time.time * 3f) * 8f * scale,
                    (float)Math.cos(Time.time * 2f) * 4f * scale - 8f * scale
                )
            );
            renderSpecularHighlight(canvas, sparklePos, 2f, pulseIntensity * 0.5f, scale);
        }
    }

    private void renderFootprints(Canvas canvas, GoosePhysics physics) {
        FootMark[] footMarks = physics.getFootMarks();
        Paint mudPaint = new Paint();
        mudPaint.setColor(0xFF8B4513);
        mudPaint.setAntiAlias(true);

        for (int i = 0; i < footMarks.length; i++) {
            if (footMarks[i] != null && footMarks[i].time != 0.0f) {
                double fadeStart = footMarks[i].time + 8.5f;
                float fadeProgress = SamMath.Clamp((float) (Time.time - fadeStart), 0f, 1f);
                float radius = SamMath.Lerp(3f, 0f, fadeProgress);
                fillCircleFromCenter(canvas, mudPaint, footMarks[i].position, (int) radius);
            }
        }
    }

    // ============== EYE RENDERING ==============

    // Eye tracking state
    private Vector2 eyeLookTarget = null;
    private float eyeLookTimer = 0f;

    /**
     * Set where the eyes should look (for tracking user touch, etc.)
     */
    public void setEyeLookTarget(Vector2 target) {
        this.eyeLookTarget = target;
        this.eyeLookTimer = 2f; // Look for 2 seconds
    }

    private void renderEyes(Canvas canvas, GooseRig rig) {
        // Update eye look timer
        if (eyeLookTimer > 0) {
            eyeLookTimer -= Time.deltaTime;
            if (eyeLookTimer <= 0) {
                eyeLookTarget = null;
            }
        }

        float scale = rig.getScale();
        float eyeRadius = 4f * scale;  // Larger eyes
        float eyeVerticalScale = rig.getEyeVerticalScale();

        // Check for heart eyes
        if (rig.hasHeartEyes()) {
            renderHeartEyes(canvas, rig);
            return;
        }

        // Check for special expressions
        GooseRig.Expression expr = rig.getCurrentExpression();

        // Draw eyes
        if (eyeVerticalScale < 0.1f || rig.isBlinking()) {
            // Eyes closed - draw curved line (happy closed eyes)
            Paint closedPaint = new Paint();
            closedPaint.setColor(eyeColor);
            closedPaint.setAntiAlias(true);
            closedPaint.setStyle(Paint.Style.STROKE);
            closedPaint.setStrokeWidth(2f * scale);
            closedPaint.setStrokeCap(Paint.Cap.ROUND);

            if (expr == GooseRig.Expression.HAPPY) {
                // Happy closed eyes (^_^)
                drawHappyClosedEye(canvas, rig.leftEyePos, eyeRadius, closedPaint);
                drawHappyClosedEye(canvas, rig.rightEyePos, eyeRadius, closedPaint);
            } else {
                // Normal closed eyes (-)
                canvas.drawLine(
                    rig.leftEyePos.x - eyeRadius,
                    rig.leftEyePos.y,
                    rig.leftEyePos.x + eyeRadius,
                    rig.leftEyePos.y,
                    closedPaint
                );
                canvas.drawLine(
                    rig.rightEyePos.x - eyeRadius,
                    rig.rightEyePos.y,
                    rig.rightEyePos.x + eyeRadius,
                    rig.rightEyePos.y,
                    closedPaint
                );
            }
        } else {
            // Draw detailed eyes with pupils
            renderDetailedEye(canvas, rig.leftEyePos, eyeRadius, eyeVerticalScale, scale, expr, false);
            renderDetailedEye(canvas, rig.rightEyePos, eyeRadius, eyeVerticalScale, scale, expr, true);
        }

        // Draw eyebrows based on expression
        renderEyebrows(canvas, rig, eyeRadius, scale, expr);
    }

    /**
     * Render a detailed eye with sclera, iris, pupil, and Fresnel effect.
     */
    private void renderDetailedEye(Canvas canvas, Vector2 pos, float radius, float vScale,
                                    float scale, GooseRig.Expression expr, boolean isRight) {
        initShadingPaints();

        RectF eyeRect = new RectF(
            pos.x - radius,
            pos.y - radius * vScale,
            pos.x + radius,
            pos.y + radius * vScale
        );

        // === FRESNEL EFFECT: Edges brighter than center ===
        // Create radial gradient for eye white with fresnel
        RadialGradient fresnelGradient = new RadialGradient(
            pos.x, pos.y,
            radius,
            new int[]{0xFFF8F8F8, 0xFFFFFFFF, 0xFFFFFFF8},
            new float[]{0f, 0.6f, 1f},
            Shader.TileMode.CLAMP
        );

        Paint scleraPaint = new Paint();
        scleraPaint.setAntiAlias(true);
        scleraPaint.setStyle(Paint.Style.FILL);
        scleraPaint.setShader(fresnelGradient);
        canvas.drawOval(eyeRect, scleraPaint);
        scleraPaint.setShader(null);

        // Eye outline with slight gradient
        Paint outlinePaint = new Paint();
        outlinePaint.setColor(darkenColor(eyeColor, 0.4f));
        outlinePaint.setAntiAlias(true);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(1.5f * scale);
        canvas.drawOval(eyeRect, outlinePaint);

        // Calculate pupil position (can track target)
        float pupilOffsetX = 0;
        float pupilOffsetY = 0;

        if (eyeLookTarget != null) {
            // Look towards target
            Vector2 toTarget = Vector2.subtract(eyeLookTarget, pos);
            float dist = Vector2.Distance(pos, eyeLookTarget);
            if (dist > 1f) {
                toTarget = Vector2.Normalize(toTarget);
                float maxOffset = radius * 0.3f;
                pupilOffsetX = toTarget.x * maxOffset;
                pupilOffsetY = toTarget.y * maxOffset * vScale;
            }
        } else {
            // Subtle random eye movement
            float time = Time.timeF();
            pupilOffsetX = (float)Math.sin(time * 0.5f + (isRight ? 1f : 0f)) * radius * 0.1f;
            pupilOffsetY = (float)Math.cos(time * 0.3f) * radius * 0.05f * vScale;
        }

        float irisX = pos.x + pupilOffsetX;
        float irisY = pos.y + pupilOffsetY;
        float irisRadius = radius * 0.6f * Math.min(1f, vScale);

        // === IRIS with gradient for depth ===
        RadialGradient irisGradient = new RadialGradient(
            irisX - irisRadius * 0.2f,
            irisY - irisRadius * 0.2f,
            irisRadius * 1.2f,
            new int[]{0xFF5A5A5A, 0xFF3A3A3A, 0xFF2A2A2A},
            new float[]{0f, 0.5f, 1f},
            Shader.TileMode.CLAMP
        );

        Paint irisPaint = new Paint();
        irisPaint.setAntiAlias(true);
        irisPaint.setShader(irisGradient);
        canvas.drawCircle(irisX, irisY, irisRadius, irisPaint);
        irisPaint.setShader(null);

        // Iris ring (limbal ring) for more realistic look
        Paint limbusRing = new Paint();
        limbusRing.setAntiAlias(true);
        limbusRing.setStyle(Paint.Style.STROKE);
        limbusRing.setStrokeWidth(1f * scale);
        limbusRing.setColor(0xFF1A1A1A);
        canvas.drawCircle(irisX, irisY, irisRadius, limbusRing);

        // === PUPIL with subtle gradient ===
        float pupilRadius = radius * 0.35f;

        // Pupil size changes with mood
        if (expr == GooseRig.Expression.SURPRISED) {
            pupilRadius *= 0.65f;  // Small pupils when surprised
        } else if (expr == GooseRig.Expression.LOVE || PetNeeds.get().happiness > 80) {
            pupilRadius *= 1.25f;  // Dilated pupils when happy/in love
        }

        pupilRadius *= Math.min(1f, vScale);

        RadialGradient pupilGradient = new RadialGradient(
            irisX, irisY,
            pupilRadius,
            new int[]{eyeColor, darkenColor(eyeColor, 0.8f)},
            null,
            Shader.TileMode.CLAMP
        );

        Paint pupilPaint = new Paint();
        pupilPaint.setAntiAlias(true);
        pupilPaint.setShader(pupilGradient);
        canvas.drawCircle(irisX, irisY, pupilRadius, pupilPaint);
        pupilPaint.setShader(null);

        // === SPECULAR HIGHLIGHTS (multiple for realism) ===
        Paint shinePaint = new Paint();
        shinePaint.setAntiAlias(true);

        // Primary catchlight (larger, top-left)
        float shineX = pos.x - radius * 0.2f;
        float shineY = pos.y - radius * 0.2f * vScale;
        float shineRadius = radius * 0.28f;

        RadialGradient shineGradient = new RadialGradient(
            shineX, shineY, shineRadius,
            new int[]{0xDDFFFFFF, 0x88FFFFFF, 0x00FFFFFF},
            new float[]{0f, 0.5f, 1f},
            Shader.TileMode.CLAMP
        );
        shinePaint.setShader(shineGradient);
        canvas.drawCircle(shineX, shineY, shineRadius, shinePaint);

        // Secondary catchlight (smaller, bottom-right)
        float shine2X = pos.x + radius * 0.15f;
        float shine2Y = pos.y + radius * 0.1f * vScale;
        float shine2Radius = radius * 0.12f;

        RadialGradient shine2Gradient = new RadialGradient(
            shine2X, shine2Y, shine2Radius,
            new int[]{0x99FFFFFF, 0x00FFFFFF},
            null,
            Shader.TileMode.CLAMP
        );
        shinePaint.setShader(shine2Gradient);
        canvas.drawCircle(shine2X, shine2Y, shine2Radius, shinePaint);

        shinePaint.setShader(null);

        // === Subtle Fresnel rim on eye edge ===
        Paint fresnelRim = new Paint();
        fresnelRim.setAntiAlias(true);
        fresnelRim.setStyle(Paint.Style.STROKE);
        fresnelRim.setStrokeWidth(2f * scale);
        fresnelRim.setColor(0x22FFFFFF);
        fresnelRim.setMaskFilter(new BlurMaskFilter(2f * scale, BlurMaskFilter.Blur.NORMAL));
        canvas.drawOval(eyeRect, fresnelRim);
        fresnelRim.setMaskFilter(null);
    }

    /**
     * Draw happy closed eye (^_^).
     */
    private void drawHappyClosedEye(Canvas canvas, Vector2 pos, float radius, Paint paint) {
        Path eyePath = new Path();
        eyePath.moveTo(pos.x - radius, pos.y + radius * 0.3f);
        eyePath.quadTo(pos.x, pos.y - radius * 0.5f, pos.x + radius, pos.y + radius * 0.3f);
        canvas.drawPath(eyePath, paint);
    }

    /**
     * Render dynamic eyebrows based on expression and brow curve.
     */
    private void renderEyebrows(Canvas canvas, GooseRig rig, float eyeRadius, float scale,
                                 GooseRig.Expression expr) {
        // Get dynamic brow curve from rig
        float browCurve = rig.getBrowCurve();

        // Skip if nearly neutral
        if (expr == GooseRig.Expression.NEUTRAL && Math.abs(browCurve) < 0.1f) return;

        Paint browPaint = new Paint();
        browPaint.setColor(darkenColor(bodyColor, 0.55f));
        browPaint.setAntiAlias(true);
        browPaint.setStyle(Paint.Style.STROKE);
        browPaint.setStrokeWidth(2.5f * scale);
        browPaint.setStrokeCap(Paint.Cap.ROUND);

        float browY = -eyeRadius * 1.6f;
        float browLength = eyeRadius * 1.3f;

        // Base brow positions
        Vector2 leftBrowStart = new Vector2(rig.leftEyePos.x - browLength, rig.leftEyePos.y + browY);
        Vector2 leftBrowEnd = new Vector2(rig.leftEyePos.x + browLength * 0.6f, rig.leftEyePos.y + browY);
        Vector2 rightBrowStart = new Vector2(rig.rightEyePos.x + browLength, rig.rightEyePos.y + browY);
        Vector2 rightBrowEnd = new Vector2(rig.rightEyePos.x - browLength * 0.6f, rig.rightEyePos.y + browY);

        // Apply expression-based modifications
        float innerRaise = 0f;
        float outerRaise = 0f;
        float curveAmount = browCurve * browLength * 0.4f;

        switch (expr) {
            case ANGRY:
                innerRaise = -browLength * 0.5f;  // Inner end lower
                outerRaise = browLength * 0.3f;   // Outer end raised
                break;
            case SAD:
                innerRaise = browLength * 0.4f;   // Inner end raised
                outerRaise = 0f;
                break;
            case SURPRISED:
                browY -= eyeRadius * 0.6f;
                // Arched brows
                curveAmount = browLength * 0.3f;
                break;
            case HAPPY:
                // Slight arch
                curveAmount = browLength * 0.2f;
                break;
            default:
                break;
        }

        // Apply brow curve from rig
        innerRaise += curveAmount;

        // Draw curved eyebrows using bezier
        Path leftBrow = new Path();
        leftBrow.moveTo(leftBrowStart.x, leftBrowStart.y + outerRaise);
        leftBrow.quadTo(
            (leftBrowStart.x + leftBrowEnd.x) / 2,
            leftBrowStart.y + browY * 0.1f - curveAmount * 0.5f,
            leftBrowEnd.x,
            leftBrowEnd.y + innerRaise
        );
        canvas.drawPath(leftBrow, browPaint);

        Path rightBrow = new Path();
        rightBrow.moveTo(rightBrowStart.x, rightBrowStart.y + outerRaise);
        rightBrow.quadTo(
            (rightBrowStart.x + rightBrowEnd.x) / 2,
            rightBrowStart.y + browY * 0.1f - curveAmount * 0.5f,
            rightBrowEnd.x,
            rightBrowEnd.y + innerRaise
        );
        canvas.drawPath(rightBrow, browPaint);

        // Render tears if SAD
        if (expr == GooseRig.Expression.SAD) {
            renderTearEffects(canvas, rig, scale);
        }
    }

    /**
     * Render tear effects when goose is sad.
     */
    private void renderTearEffects(Canvas canvas, GooseRig rig, float scale) {
        float tearAmount = rig.getTearAmount();
        if (tearAmount < 0.1f) return;

        Paint tearPaint = new Paint();
        tearPaint.setAntiAlias(true);

        // Tear color with gradient
        int tearColor = Color.argb((int)(tearAmount * 180), 135, 206, 250);

        // Animated tear position
        float tearPhase = (float) ((Time.time * 2f) % 1f);
        float tearY = tearPhase * 15f * scale;

        // Left eye tear
        Vector2 leftTearPos = new Vector2(
            rig.leftEyePos.x + 1f * scale,
            rig.leftEyePos.y + 4f * scale + tearY
        );

        // Right eye tear (slightly offset timing)
        float rightPhase = (float) (((Time.time * 2f) + 0.5f) % 1f);
        float rightTearY = rightPhase * 15f * scale;
        Vector2 rightTearPos = new Vector2(
            rig.rightEyePos.x - 1f * scale,
            rig.rightEyePos.y + 4f * scale + rightTearY
        );

        // Tear drop shape
        float tearSize = 2.5f * scale * tearAmount;
        float tearAlpha = (1f - tearPhase) * tearAmount;

        // Draw tears with gradient
        RadialGradient tearGradient = new RadialGradient(
            leftTearPos.x, leftTearPos.y - tearSize * 0.5f,
            tearSize * 1.5f,
            new int[]{
                Color.argb((int)(tearAlpha * 220), 200, 230, 255),
                Color.argb((int)(tearAlpha * 150), 135, 206, 250),
                Color.argb(0, 135, 206, 250)
            },
            new float[]{0f, 0.5f, 1f},
            Shader.TileMode.CLAMP
        );

        tearPaint.setShader(tearGradient);

        // Draw tear drop shape (elongated oval)
        Path tearPath = new Path();
        tearPath.moveTo(leftTearPos.x, leftTearPos.y - tearSize);
        tearPath.quadTo(
            leftTearPos.x + tearSize * 0.8f,
            leftTearPos.y,
            leftTearPos.x,
            leftTearPos.y + tearSize * 1.5f
        );
        tearPath.quadTo(
            leftTearPos.x - tearSize * 0.8f,
            leftTearPos.y,
            leftTearPos.x,
            leftTearPos.y - tearSize
        );
        canvas.drawPath(tearPath, tearPaint);

        // Right tear with adjusted gradient
        float rightAlpha = (1f - rightPhase) * tearAmount;
        RadialGradient rightTearGrad = new RadialGradient(
            rightTearPos.x, rightTearPos.y - tearSize * 0.5f,
            tearSize * 1.5f,
            new int[]{
                Color.argb((int)(rightAlpha * 220), 200, 230, 255),
                Color.argb((int)(rightAlpha * 150), 135, 206, 250),
                Color.argb(0, 135, 206, 250)
            },
            new float[]{0f, 0.5f, 1f},
            Shader.TileMode.CLAMP
        );
        tearPaint.setShader(rightTearGrad);

        Path rightTearPath = new Path();
        rightTearPath.moveTo(rightTearPos.x, rightTearPos.y - tearSize);
        rightTearPath.quadTo(
            rightTearPos.x + tearSize * 0.8f,
            rightTearPos.y,
            rightTearPos.x,
            rightTearPos.y + tearSize * 1.5f
        );
        rightTearPath.quadTo(
            rightTearPos.x - tearSize * 0.8f,
            rightTearPos.y,
            rightTearPos.x,
            rightTearPos.y - tearSize
        );
        canvas.drawPath(rightTearPath, tearPaint);

        tearPaint.setShader(null);

        // Wet shine under eyes
        Paint wetPaint = new Paint();
        wetPaint.setAntiAlias(true);
        wetPaint.setColor(Color.argb((int)(tearAmount * 60), 255, 255, 255));
        canvas.drawOval(new RectF(
            rig.leftEyePos.x - 3f * scale,
            rig.leftEyePos.y + 3f * scale,
            rig.leftEyePos.x + 3f * scale,
            rig.leftEyePos.y + 5f * scale
        ), wetPaint);
        canvas.drawOval(new RectF(
            rig.rightEyePos.x - 3f * scale,
            rig.rightEyePos.y + 3f * scale,
            rig.rightEyePos.x + 3f * scale,
            rig.rightEyePos.y + 5f * scale
        ), wetPaint);
    }

    private void renderHeartEyes(Canvas canvas, GooseRig rig) {
        Paint heartPaint = new Paint();
        heartPaint.setColor(0xFFFF69B4);
        heartPaint.setAntiAlias(true);
        heartPaint.setStyle(Paint.Style.FILL);

        float size = 5f * rig.getScale();

        // Pulsing effect
        float pulse = 1f + (float)Math.sin(Time.time * 5f) * 0.15f;
        size *= pulse;

        drawHeart(canvas, heartPaint, rig.leftEyePos, size);
        drawHeart(canvas, heartPaint, rig.rightEyePos, size);

        // Add sparkle
        if (Math.random() < 0.1f) {
            spawnParticle(Vector2.add(rig.leftEyePos, new Vector2(
                SamMath.RandomRange(-5, 5), SamMath.RandomRange(-5, 5)
            )), ParticleType.SPARKLE);
        }
    }

    // ============== ACCESSORY RENDERING ==============

    private void renderAccessories(Canvas canvas, GooseRig rig) {
        if (!PetAppearance.get().hasAccessories()) return;

        Paint accessoryPaint = new Paint();
        accessoryPaint.setAntiAlias(true);

        // Hat rendering
        if (PetAppearance.get().hatId > 0) {
            Vector2 headPos = rig.head1EndPoint;

            switch (PetAppearance.get().hatId) {
                case 1: // Bow (lazo mejorado)
                    accessoryPaint.setColor(0xFFFF69B4);
                    accessoryPaint.setStyle(Paint.Style.FILL);
                    // Left loop
                    canvas.drawOval(new RectF(headPos.x - 14, headPos.y - 18, headPos.x - 2, headPos.y - 8), accessoryPaint);
                    // Right loop
                    canvas.drawOval(new RectF(headPos.x + 2, headPos.y - 18, headPos.x + 14, headPos.y - 8), accessoryPaint);
                    // Center knot
                    accessoryPaint.setColor(0xFFFF1493);
                    fillCircleFromCenter(canvas, accessoryPaint, Vector2.add(headPos, new Vector2(0, -13)), 4);
                    // Ribbon tails
                    accessoryPaint.setColor(0xFFFF69B4);
                    Path ribbon = new Path();
                    ribbon.moveTo(headPos.x - 3, headPos.y - 10);
                    ribbon.lineTo(headPos.x - 6, headPos.y + 2);
                    ribbon.lineTo(headPos.x - 2, headPos.y - 2);
                    ribbon.close();
                    canvas.drawPath(ribbon, accessoryPaint);
                    ribbon.reset();
                    ribbon.moveTo(headPos.x + 3, headPos.y - 10);
                    ribbon.lineTo(headPos.x + 6, headPos.y + 2);
                    ribbon.lineTo(headPos.x + 2, headPos.y - 2);
                    ribbon.close();
                    canvas.drawPath(ribbon, accessoryPaint);
                    break;
                case 2: // Top Hat
                    accessoryPaint.setColor(0xFF333333);
                    accessoryPaint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(headPos.x - 10, headPos.y - 20, headPos.x + 10, headPos.y - 15, accessoryPaint);
                    canvas.drawRect(headPos.x - 6, headPos.y - 35, headPos.x + 6, headPos.y - 20, accessoryPaint);
                    break;
                case 3: // Crown
                    accessoryPaint.setColor(0xFFFFD700);
                    canvas.drawRect(headPos.x - 8, headPos.y - 18, headPos.x + 8, headPos.y - 12, accessoryPaint);
                    Path crownPath = new Path();
                    crownPath.moveTo(headPos.x - 8, headPos.y - 18);
                    crownPath.lineTo(headPos.x - 6, headPos.y - 25);
                    crownPath.lineTo(headPos.x - 2, headPos.y - 18);
                    crownPath.lineTo(headPos.x, headPos.y - 28);
                    crownPath.lineTo(headPos.x + 2, headPos.y - 18);
                    crownPath.lineTo(headPos.x + 6, headPos.y - 25);
                    crownPath.lineTo(headPos.x + 8, headPos.y - 18);
                    crownPath.close();
                    canvas.drawPath(crownPath, accessoryPaint);
                    break;
                case 4: // Cap
                    accessoryPaint.setColor(0xFF4169E1);
                    canvas.drawArc(new RectF(headPos.x - 10, headPos.y - 22, headPos.x + 10, headPos.y - 8),
                        180, 180, true, accessoryPaint);
                    canvas.drawRect(headPos.x - 12, headPos.y - 10, headPos.x + 12, headPos.y - 8, accessoryPaint);
                    break;
            }
        }

        // Accessory rendering
        if (PetAppearance.get().accessoryId > 0) {
            Vector2 neckPos = rig.neckBase;
            accessoryPaint.setStrokeWidth(3f);

            switch (PetAppearance.get().accessoryId) {
                case 1: // Scarf
                    accessoryPaint.setColor(0xFFDC143C);
                    accessoryPaint.setStyle(Paint.Style.STROKE);
                    canvas.drawArc(new RectF(neckPos.x - 12, neckPos.y - 5, neckPos.x + 12, neckPos.y + 10),
                        0, 180, false, accessoryPaint);
                    accessoryPaint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(neckPos.x + 8, neckPos.y, neckPos.x + 12, neckPos.y + 20, accessoryPaint);
                    break;
                case 2: // Glasses
                    accessoryPaint.setColor(0xFF000000);
                    accessoryPaint.setStyle(Paint.Style.STROKE);
                    Vector2 eyeCenter = rig.neckHeadPoint;
                    canvas.drawCircle(eyeCenter.x - 5, eyeCenter.y - 3, 4, accessoryPaint);
                    canvas.drawCircle(eyeCenter.x + 5, eyeCenter.y - 3, 4, accessoryPaint);
                    canvas.drawLine(eyeCenter.x - 1, eyeCenter.y - 3, eyeCenter.x + 1, eyeCenter.y - 3, accessoryPaint);
                    break;
                case 3: // Collar
                    accessoryPaint.setColor(0xFF8B4513);
                    accessoryPaint.setStyle(Paint.Style.STROKE);
                    accessoryPaint.setStrokeWidth(4f);
                    canvas.drawArc(new RectF(neckPos.x - 10, neckPos.y - 3, neckPos.x + 10, neckPos.y + 8),
                        0, 180, false, accessoryPaint);
                    Paint tagPaint = new Paint();
                    tagPaint.setColor(0xFFFFD700);
                    tagPaint.setAntiAlias(true);
                    fillCircleFromCenter(canvas, tagPaint, new Vector2(neckPos.x, neckPos.y + 8), 4);
                    break;
                case 4: // Bow Tie
                    accessoryPaint.setColor(0xFF8B0000);
                    accessoryPaint.setStyle(Paint.Style.FILL);
                    canvas.drawOval(new RectF(neckPos.x - 12, neckPos.y - 4, neckPos.x - 2, neckPos.y + 4), accessoryPaint);
                    canvas.drawOval(new RectF(neckPos.x + 2, neckPos.y - 4, neckPos.x + 12, neckPos.y + 4), accessoryPaint);
                    accessoryPaint.setColor(0xFF600000);
                    fillCircleFromCenter(canvas, accessoryPaint, neckPos, 3);
                    break;
            }
        }
    }

    // ============== UI RENDERING ==============

    private void renderEmoji(Canvas canvas, Vector2 position, GooseTouchHandler touchHandler) {
        String emoji = touchHandler.getCurrentEmoji();
        if (emoji.isEmpty()) return;

        Paint textPaint = new Paint();
        textPaint.setColor(0xFF000000);
        textPaint.setTextSize(24f);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        Paint bubblePaint = new Paint();
        bubblePaint.setColor(0xFFFFFFFF);
        bubblePaint.setAntiAlias(true);

        Vector2 emojiPos = new Vector2(position.x, position.y - 60);
        float textWidth = textPaint.measureText(emoji);
        float padding = 8f;

        canvas.drawRoundRect(new RectF(
            emojiPos.x - textWidth/2 - padding,
            emojiPos.y - 18,
            emojiPos.x + textWidth/2 + padding,
            emojiPos.y + 8
        ), 8, 8, bubblePaint);

        Paint outlinePaint = new Paint();
        outlinePaint.setColor(0xFF333333);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(2f);
        outlinePaint.setAntiAlias(true);
        canvas.drawRoundRect(new RectF(
            emojiPos.x - textWidth/2 - padding,
            emojiPos.y - 18,
            emojiPos.x + textWidth/2 + padding,
            emojiPos.y + 8
        ), 8, 8, outlinePaint);

        canvas.drawText(emoji, emojiPos.x, emojiPos.y, textPaint);
    }

    /**
     * Render the thought bubble showing what the goose is thinking.
     */
    private void renderThoughtBubble(Canvas canvas, Vector2 position) {
        String thought = TheGoose.getCurrentThought();
        if (thought == null || thought.isEmpty()) return;

        float alpha = TheGoose.getThoughtAlpha();
        if (alpha <= 0) return;

        int alphaInt = (int)(alpha * 255);

        // Position the thought bubble above and to the right of the goose
        float bubbleX = position.x + 45f;
        float bubbleY = position.y - 85f;

        Paint textPaint = new Paint();
        textPaint.setColor(Color.argb(alphaInt, 50, 50, 50));
        textPaint.setTextSize(16f);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);

        // Measure text
        float textWidth = textPaint.measureText(thought);
        float padding = 12f;
        float maxWidth = 150f;

        // Handle multi-line if needed
        if (textWidth > maxWidth) {
            textPaint.setTextSize(14f);
            textWidth = Math.min(textPaint.measureText(thought), maxWidth);
        }

        // Bubble background
        Paint bubblePaint = new Paint();
        bubblePaint.setColor(Color.argb(alphaInt, 255, 255, 255));
        bubblePaint.setAntiAlias(true);
        bubblePaint.setShadowLayer(4f, 2f, 2f, Color.argb(alphaInt / 3, 0, 0, 0));

        float bubbleLeft = bubbleX - padding;
        float bubbleTop = bubbleY - 20f;
        float bubbleRight = bubbleX + textWidth + padding;
        float bubbleBottom = bubbleY + 8f;

        // Draw main bubble
        RectF bubbleRect = new RectF(bubbleLeft, bubbleTop, bubbleRight, bubbleBottom);
        canvas.drawRoundRect(bubbleRect, 10f, 10f, bubblePaint);

        // Draw thought bubble tail (three circles getting smaller)
        bubblePaint.setShadowLayer(0, 0, 0, 0);
        float tailX = bubbleLeft;
        float tailY = bubbleBottom;

        // Largest circle
        canvas.drawCircle(tailX - 2f, tailY + 8f, 6f, bubblePaint);
        // Medium circle
        canvas.drawCircle(tailX - 8f, tailY + 16f, 4f, bubblePaint);
        // Smallest circle
        canvas.drawCircle(tailX - 12f, tailY + 22f, 2.5f, bubblePaint);

        // Draw bubble outline
        Paint outlinePaint = new Paint();
        outlinePaint.setColor(Color.argb(alphaInt, 180, 180, 180));
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(1.5f);
        outlinePaint.setAntiAlias(true);
        canvas.drawRoundRect(bubbleRect, 10f, 10f, outlinePaint);

        // Draw circles outlines
        canvas.drawCircle(tailX - 2f, tailY + 8f, 6f, outlinePaint);
        canvas.drawCircle(tailX - 8f, tailY + 16f, 4f, outlinePaint);
        canvas.drawCircle(tailX - 12f, tailY + 22f, 2.5f, outlinePaint);

        // Draw text
        canvas.drawText(thought, bubbleX, bubbleY, textPaint);

        // Draw small thinking indicator (three dots that pulse)
        if (alpha > 0.5f) {
            float dotPhase = (float) ((Time.time * 3f) % 1f);
            Paint dotPaint = new Paint();
            dotPaint.setAntiAlias(true);

            for (int i = 0; i < 3; i++) {
                float dotAlpha = (float)Math.sin((dotPhase + i * 0.3f) * Math.PI);
                dotAlpha = Math.max(0.3f, dotAlpha);
                dotPaint.setColor(Color.argb((int)(dotAlpha * alphaInt * 0.5f), 100, 100, 100));

                float dotX = bubbleRight - 20f + i * 6f;
                float dotY = bubbleTop + 5f;
                canvas.drawCircle(dotX, dotY, 2f, dotPaint);
            }
        }
    }

    private void renderSleepIndicator(Canvas canvas, Vector2 position, GooseAI ai) {
        if (ai.getCurrentTask() != GooseTasks.GooseTask.Sleeping) return;

        Paint textPaint = new Paint();
        textPaint.setColor(0xFF6666FF);
        textPaint.setAntiAlias(true);

        float elapsed = (float) (Time.time - ai.getSleepStartTime());
        float yOffset = (elapsed % 1f) * 20f;

        textPaint.setTextSize(18f);
        canvas.drawText("Z", position.x + 20, position.y - 40 - yOffset, textPaint);
        textPaint.setTextSize(14f);
        canvas.drawText("z", position.x + 30, position.y - 50 - yOffset * 0.8f, textPaint);
        textPaint.setTextSize(10f);
        canvas.drawText("z", position.x + 38, position.y - 58 - yOffset * 0.6f, textPaint);
    }

    // ============== ACHIEVEMENT NOTIFICATION RENDERING ==============

    /**
     * Render the achievement notification banner.
     * Slides in from top of screen with elegant animation.
     */
    private void renderAchievementNotification(Canvas canvas, int screenWidth, int screenHeight) {
        TheGoose.AchievementNotification notification = TheGoose.getCurrentAchievementNotification();
        if (notification == null) return;

        TheGoose.Achievement achievement = notification.achievement;
        float animPhase = notification.animPhase;

        // Animation: slide in from top
        float slideOffset = (1f - easeOutBack(animPhase)) * -120f;

        // Banner dimensions
        float bannerWidth = Math.min(screenWidth * 0.85f, 350f);
        float bannerHeight = 80f;
        float bannerX = (screenWidth - bannerWidth) / 2f;
        float bannerY = 40f + slideOffset;

        // Shadow
        Paint shadowPaint = new Paint();
        shadowPaint.setColor(Color.argb((int)(60 * animPhase), 0, 0, 0));
        shadowPaint.setAntiAlias(true);
        shadowPaint.setMaskFilter(new BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL));
        canvas.drawRoundRect(bannerX + 4, bannerY + 6, bannerX + bannerWidth + 4,
                bannerY + bannerHeight + 6, 16f, 16f, shadowPaint);

        // Main banner gradient background
        Paint bgPaint = new Paint();
        bgPaint.setAntiAlias(true);
        LinearGradient gradient = new LinearGradient(
                bannerX, bannerY, bannerX + bannerWidth, bannerY + bannerHeight,
                new int[]{0xFFFFD700, 0xFFFFA500, 0xFFFF8C00},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        bgPaint.setShader(gradient);
        canvas.drawRoundRect(bannerX, bannerY, bannerX + bannerWidth,
                bannerY + bannerHeight, 16f, 16f, bgPaint);

        // Inner glow
        Paint glowPaint = new Paint();
        glowPaint.setAntiAlias(true);
        RadialGradient innerGlow = new RadialGradient(
                bannerX + bannerWidth / 2, bannerY + bannerHeight / 2,
                bannerWidth / 2,
                Color.argb((int)(80 * animPhase), 255, 255, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        );
        glowPaint.setShader(innerGlow);
        canvas.drawRoundRect(bannerX, bannerY, bannerX + bannerWidth,
                bannerY + bannerHeight, 16f, 16f, glowPaint);

        // Border
        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.argb((int)(200 * animPhase), 255, 255, 255));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
        borderPaint.setAntiAlias(true);
        canvas.drawRoundRect(bannerX + 1, bannerY + 1, bannerX + bannerWidth - 1,
                bannerY + bannerHeight - 1, 15f, 15f, borderPaint);

        // Icon circle background
        float iconSize = 50f;
        float iconX = bannerX + 20f + iconSize / 2;
        float iconY = bannerY + bannerHeight / 2;

        Paint iconBgPaint = new Paint();
        iconBgPaint.setColor(Color.argb((int)(220 * animPhase), 255, 255, 255));
        iconBgPaint.setAntiAlias(true);
        canvas.drawCircle(iconX, iconY, iconSize / 2 + 3, iconBgPaint);

        // Achievement icon (emoji)
        Paint iconPaint = new Paint();
        iconPaint.setTextSize(32f);
        iconPaint.setTextAlign(Paint.Align.CENTER);
        iconPaint.setAntiAlias(true);
        iconPaint.setColor(Color.argb((int)(255 * animPhase), 0, 0, 0));
        canvas.drawText(achievement.icon, iconX, iconY + 10f, iconPaint);

        // "Achievement Unlocked!" text
        Paint headerPaint = new Paint();
        headerPaint.setColor(Color.argb((int)(255 * animPhase), 255, 255, 255));
        headerPaint.setTextSize(12f);
        headerPaint.setAntiAlias(true);
        headerPaint.setTypeface(Typeface.DEFAULT_BOLD);
        headerPaint.setLetterSpacing(0.1f);
        float textStartX = bannerX + 20f + iconSize + 15f;
        canvas.drawText("ACHIEVEMENT UNLOCKED!", textStartX, bannerY + 22f, headerPaint);

        // Achievement name
        Paint namePaint = new Paint();
        namePaint.setColor(Color.argb((int)(255 * animPhase), 80, 40, 0));
        namePaint.setTextSize(18f);
        namePaint.setAntiAlias(true);
        namePaint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(achievement.name, textStartX, bannerY + 44f, namePaint);

        // Achievement description
        Paint descPaint = new Paint();
        descPaint.setColor(Color.argb((int)(200 * animPhase), 80, 50, 20));
        descPaint.setTextSize(11f);
        descPaint.setAntiAlias(true);

        // Truncate if too long
        String desc = achievement.description;
        float maxDescWidth = bannerWidth - iconSize - 50f;
        while (descPaint.measureText(desc) > maxDescWidth && desc.length() > 3) {
            desc = desc.substring(0, desc.length() - 4) + "...";
        }
        canvas.drawText(desc, textStartX, bannerY + 62f, descPaint);

        // Sparkle particles around banner
        if (animPhase > 0.5f) {
            renderNotificationSparkles(canvas, bannerX + bannerWidth / 2, bannerY + bannerHeight / 2,
                    bannerWidth, bannerHeight, animPhase);
        }

        // Pending notifications indicator
        int pending = TheGoose.getPendingNotificationCount();
        if (pending > 1) {
            Paint counterPaint = new Paint();
            counterPaint.setColor(Color.argb((int)(220 * animPhase), 255, 100, 100));
            counterPaint.setAntiAlias(true);
            float counterX = bannerX + bannerWidth - 20f;
            float counterY = bannerY + bannerHeight - 10f;
            canvas.drawCircle(counterX, counterY, 12f, counterPaint);

            Paint counterTextPaint = new Paint();
            counterTextPaint.setColor(Color.WHITE);
            counterTextPaint.setTextSize(12f);
            counterTextPaint.setTextAlign(Paint.Align.CENTER);
            counterTextPaint.setAntiAlias(true);
            counterTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("+" + (pending - 1), counterX, counterY + 4f, counterTextPaint);
        }
    }

    /**
     * Render sparkle particles around the notification banner.
     */
    private void renderNotificationSparkles(Canvas canvas, float centerX, float centerY,
                                            float width, float height, float alpha) {
        Paint sparklePaint = new Paint();
        sparklePaint.setAntiAlias(true);

        float time = Time.timeF() * 2f;

        for (int i = 0; i < 8; i++) {
            float angle = (float)(i * Math.PI / 4 + time * 0.5f);
            float distance = width / 2 + 15f + (float)Math.sin(time * 3 + i) * 10f;
            float x = centerX + (float)Math.cos(angle) * distance * 0.6f;
            float y = centerY + (float)Math.sin(angle) * distance * 0.3f;

            float sparkleAlpha = (float)(0.3f + 0.7f * Math.abs(Math.sin(time * 4 + i * 0.7f)));
            float size = 3f + 2f * (float)Math.abs(Math.sin(time * 5 + i * 1.2f));

            sparklePaint.setColor(Color.argb((int)(255 * alpha * sparkleAlpha), 255, 255, 200));
            canvas.drawCircle(x, y, size, sparklePaint);

            // Cross sparkle effect
            sparklePaint.setStrokeWidth(1.5f);
            sparklePaint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(x - size * 1.5f, y, x + size * 1.5f, y, sparklePaint);
            canvas.drawLine(x, y - size * 1.5f, x, y + size * 1.5f, sparklePaint);
            sparklePaint.setStyle(Paint.Style.FILL);
        }
    }

    /**
     * Ease out back function for bouncy animation.
     */
    private float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (float)Math.pow(t - 1, 3) + c1 * (float)Math.pow(t - 1, 2);
    }

    // ============== SHAPE DRAWING UTILITIES ==============

    /**
     * Draw a heart shape.
     */
    private void drawHeart(Canvas canvas, Paint paint, Vector2 pos, float size) {
        Path heart = new Path();
        float x = pos.x;
        float y = pos.y;
        float s = size * 0.5f;

        heart.moveTo(x, y + s * 0.3f);
        heart.cubicTo(x - s, y - s * 0.5f, x - s * 1.5f, y + s * 0.3f, x, y + s);
        heart.cubicTo(x + s * 1.5f, y + s * 0.3f, x + s, y - s * 0.5f, x, y + s * 0.3f);
        heart.close();

        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(heart, paint);
    }

    /**
     * Draw a 5-pointed star.
     */
    private void drawStar(Canvas canvas, Paint paint, Vector2 pos, float size) {
        Path star = new Path();
        float outerRadius = size;
        float innerRadius = size * 0.4f;

        for (int i = 0; i < 10; i++) {
            float radius = (i % 2 == 0) ? outerRadius : innerRadius;
            float angle = (float)(i * Math.PI / 5 - Math.PI / 2);
            float px = pos.x + (float)Math.cos(angle) * radius;
            float py = pos.y + (float)Math.sin(angle) * radius;

            if (i == 0) {
                star.moveTo(px, py);
            } else {
                star.lineTo(px, py);
            }
        }
        star.close();

        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(star, paint);
    }

    /**
     * Draw a music note.
     */
    private void drawMusicNote(Canvas canvas, Paint paint, Vector2 pos, float size) {
        paint.setStyle(Paint.Style.FILL);

        // Note head
        canvas.drawOval(new RectF(
            pos.x - size * 0.4f,
            pos.y + size * 0.3f,
            pos.x + size * 0.3f,
            pos.y + size * 0.7f
        ), paint);

        // Stem
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.15f);
        canvas.drawLine(pos.x + size * 0.25f, pos.y + size * 0.4f,
                       pos.x + size * 0.25f, pos.y - size * 0.5f, paint);

        // Flag
        Path flag = new Path();
        flag.moveTo(pos.x + size * 0.25f, pos.y - size * 0.5f);
        flag.quadTo(pos.x + size * 0.6f, pos.y - size * 0.3f,
                   pos.x + size * 0.4f, pos.y);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(flag, paint);
    }

    /**
     * Draw a sweat drop.
     */
    private void drawSweatDrop(Canvas canvas, Paint paint, Vector2 pos, float size) {
        Path drop = new Path();
        drop.moveTo(pos.x, pos.y - size);
        drop.quadTo(pos.x + size * 0.6f, pos.y, pos.x, pos.y + size * 0.5f);
        drop.quadTo(pos.x - size * 0.6f, pos.y, pos.x, pos.y - size);
        drop.close();

        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(drop, paint);
    }

    /**
     * Draw a sparkle/twinkle.
     */
    private void drawSparkle(Canvas canvas, Paint paint, Vector2 pos, float size) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);

        // Four pointed sparkle
        canvas.drawLine(pos.x - size, pos.y, pos.x + size, pos.y, paint);
        canvas.drawLine(pos.x, pos.y - size, pos.x, pos.y + size, paint);

        // Diagonal lines (smaller)
        float diagSize = size * 0.6f;
        canvas.drawLine(pos.x - diagSize, pos.y - diagSize,
                       pos.x + diagSize, pos.y + diagSize, paint);
        canvas.drawLine(pos.x + diagSize, pos.y - diagSize,
                       pos.x - diagSize, pos.y + diagSize, paint);
    }

    /**
     * Draw anger mark (cross veins).
     */
    private void drawAngerMark(Canvas canvas, Paint paint, Vector2 pos, float size) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);

        canvas.drawLine(pos.x - size, pos.y - size,
                       pos.x + size, pos.y + size, paint);
        canvas.drawLine(pos.x + size, pos.y - size,
                       pos.x - size, pos.y + size, paint);
    }

    // ============== BASIC DRAWING UTILITIES ==============

    public void fillEllipseFromCenter(Canvas canvas, Paint paint, int x, int y, int xRadius, int yRadius) {
        canvas.drawOval(new RectF(x - xRadius, y - yRadius, x + xRadius, y + yRadius), paint);
    }

    public void fillCircleFromCenter(Canvas canvas, Paint paint, Vector2 pos, int radius) {
        fillEllipseFromCenter(canvas, paint, (int) pos.x, (int) pos.y, radius, radius);
    }

    private void drawLine(Canvas canvas, Paint paint, Vector2 start, Vector2 end) {
        canvas.drawLine(start.x, start.y, end.x, end.y, paint);
    }

    // ============== PUBLIC METHODS FOR EXTERNAL TRIGGERS ==============

    /**
     * Trigger a celebration effect (confetti burst).
     */
    public void triggerCelebration(Vector2 position) {
        spawnParticleBurst(position, ParticleType.CONFETTI, 20);
        spawnParticleBurst(position, ParticleType.STAR, 8);
    }

    /**
     * Trigger love effect (heart burst).
     */
    public void triggerLoveEffect(Vector2 position) {
        spawnParticleBurst(position, ParticleType.HEART, 10);
        spawnParticleBurst(position, ParticleType.SPARKLE, 5);
    }

    /**
     * Trigger excitement effect (stars).
     */
    public void triggerExcitement(Vector2 position) {
        spawnParticleBurst(position, ParticleType.STAR, 12);
    }

    /**
     * Clear all particles.
     */
    public void clearParticles() {
        particles.clear();
    }
}
