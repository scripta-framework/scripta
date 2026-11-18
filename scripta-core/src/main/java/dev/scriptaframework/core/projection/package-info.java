/**
 * Projections: background readers that process committed events in global order and remember
 * how far they got. A {@link dev.scriptaframework.core.projection.Projection Projection} decides
 * what to do with each event; a {@link dev.scriptaframework.core.projection.ProjectionRunner
 * ProjectionRunner} feeds it events and keeps its checkpoint in a
 * {@link dev.scriptaframework.core.projection.CheckpointStore CheckpointStore}.
 */
package dev.scriptaframework.core.projection;
