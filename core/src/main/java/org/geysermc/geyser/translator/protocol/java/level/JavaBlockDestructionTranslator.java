/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.translator.protocol.java.level;

import com.google.common.cache.Cache;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.BlockBreakHandler;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.BlockUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.BlockBreakStage;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockDestructionPacket;

import java.util.Collections;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Java sends a destruction stage per breaker, while Bedrock animates one crack per position at a given rate
 * (progress per tick, out of 65535). We estimate that rate from how quickly the stages arrive.
 */
@Translator(packet = ClientboundBlockDestructionPacket.class)
public class JavaBlockDestructionTranslator extends PacketTranslator<ClientboundBlockDestructionPacket> {
    /**
     * Used until a second stage tells us how fast the block is being broken; a full break would take 5 minutes.
     */
    private static final int PLACEHOLDER_RATE = 65535 / 6000;
    /**
     * Bedrock always starts the crack from zero, so when we first see a block part-way through being broken,
     * animate up to its current stage over this many ticks.
     */
    private static final int CATCH_UP_TICKS = 4;

    @Override
    public void translate(GeyserSession session, ClientboundBlockDestructionPacket packet) {
        BlockBreakHandler handler = session.getBlockBreakHandler();
        int breakerId = packet.getBreakerEntityId();
        Vector3i position = packet.getPosition();
        // Like the Java client, a breaker only has progress on one position at a time
        Vector3i previousPosition = handler.getDestructionBreakerPositions().getIfPresent(breakerId);

        if (packet.getStage() == BlockBreakStage.RESET) {
            handler.getDestructionBreakerPositions().invalidate(breakerId);
            removeBreaker(session, previousPosition == null ? position : previousPosition, breakerId);
            return;
        }

        if (previousPosition != null && !previousPosition.equals(position)) {
            removeBreaker(session, previousPosition, breakerId);
        }
        handler.getDestructionBreakerPositions().put(breakerId, position);

        DestructionState state = handler.getDestructionStageCache().getIfPresent(position);
        if (state == null) {
            state = new DestructionState();
            handler.getDestructionStageCache().put(position, state);
        }
        state.breakers.put(breakerId, packet.getStage());
        updateAnimation(session, position, state);
    }

    private static void removeBreaker(GeyserSession session, Vector3i position, int breakerId) {
        Cache<Vector3i, DestructionState> cache = session.getBlockBreakHandler().getDestructionStageCache();
        DestructionState state = cache.getIfPresent(position);
        if (state != null) {
            state.breakers.remove(breakerId);
            if (!state.breakers.isEmpty()) {
                // Someone else is still breaking this block, so show their progress instead
                updateAnimation(session, position, state);
                return;
            }
            state.cancelCatchUp();
            cache.invalidate(position);
        }
        BlockUtils.sendBedrockStopBlockBreak(session, position.toFloat());
    }

    private static void updateAnimation(GeyserSession session, Vector3i position, DestructionState state) {
        // The Java client shows the furthest progress of everyone breaking the block
        BlockBreakStage stage = Collections.max(state.breakers.values());
        if (stage == state.stage) {
            return;
        }

        long tick = session.getClientTicks();
        if (state.stage == null || stage.compareTo(state.stage) < 0) {
            startAnimation(session, position, state, stage, tick);
            return;
        }

        double displayedProgress = state.progressAt(tick);
        double ticksPerStage = Math.max(1, (double) Math.max(0L, tick - state.stageTick) / stage.compareTo(state.stage));
        double nextProgress = (stage.ordinal() + 1) / 10D;

        // Catch up to the next Java stage over one estimated stage interval, including any initial visual delay.
        int rate = (int) Math.clamp(Math.ceil((nextProgress - displayedProgress) * 65535 / ticksPerStage), 0, 65535);
        state.stage = stage;
        state.stageTick = tick;
        state.setRate(tick, displayedProgress, rate);
        sendLevelEvent(session, position, LevelEvent.BLOCK_UPDATE_BREAK, rate);
    }

    private static void startAnimation(GeyserSession session, Vector3i position, DestructionState state, BlockBreakStage stage, long tick) {
        state.stage = stage;
        state.stageTick = tick;

        double stageProgress = stage.ordinal() / 10D;
        if (stageProgress <= 0) {
            state.setRate(tick, 0, PLACEHOLDER_RATE);
            sendLevelEvent(session, position, LevelEvent.BLOCK_START_BREAK, PLACEHOLDER_RATE);
            return;
        }

        // We joined part-way through (came into range, or another breaker took over), so quickly animate up to this stage
        int catchUpRate = (int) Math.ceil(stageProgress * 65535 / CATCH_UP_TICKS);
        state.setRate(tick, 0, catchUpRate);
        sendLevelEvent(session, position, LevelEvent.BLOCK_START_BREAK, catchUpRate);
        scheduleCatchUpEnd(session, position, state, CATCH_UP_TICKS);
    }

    private static void scheduleCatchUpEnd(GeyserSession session, Vector3i position, DestructionState state, long delayTicks) {
        state.catchUp = session.scheduleInEventLoop(() -> {
            state.catchUp = null;
            if (session.getBlockBreakHandler().getDestructionStageCache().getIfPresent(position) != state) {
                return;
            }

            // Go by the client's ticks rather than wall time, as that is what the client animates with
            long now = session.getClientTicks();
            long remainingTicks = CATCH_UP_TICKS - (now - state.rateTick);
            if (remainingTicks > 0) {
                scheduleCatchUpEnd(session, position, state, remainingTicks);
                return;
            }

            state.setRate(now, state.progressAt(now), PLACEHOLDER_RATE);
            sendLevelEvent(session, position, LevelEvent.BLOCK_UPDATE_BREAK, PLACEHOLDER_RATE);
        }, delayTicks * 50, TimeUnit.MILLISECONDS);
    }

    private static void sendLevelEvent(GeyserSession session, Vector3i position, LevelEvent type, int rate) {
        LevelEventPacket levelEventPacket = new LevelEventPacket();
        levelEventPacket.setType(type);
        levelEventPacket.setPosition(position.toFloat());
        levelEventPacket.setData(rate);
        session.sendUpstreamPacket(levelEventPacket);
    }

    /**
     * The destruction progress of one position, as shown to the Bedrock client.
     */
    public static final class DestructionState {
        private final Int2ObjectMap<BlockBreakStage> breakers = new Int2ObjectOpenHashMap<>(1);
        /**
         * The Java stage the crack is currently animating towards, or null before anything was sent
         */
        private @Nullable BlockBreakStage stage;
        private long stageTick;
        private long rateTick;
        private double rateStartProgress;
        private int rate;
        private @Nullable ScheduledFuture<?> catchUp;

        private double progressAt(long tick) {
            return Math.clamp(rateStartProgress + rate * (double) Math.max(0L, tick - rateTick) / 65535, 0, 1);
        }

        private void setRate(long tick, double progress, int rate) {
            cancelCatchUp();
            this.rateTick = tick;
            this.rateStartProgress = progress;
            this.rate = rate;
        }

        private void cancelCatchUp() {
            if (catchUp != null) {
                catchUp.cancel(false);
                catchUp = null;
            }
        }
    }
}
