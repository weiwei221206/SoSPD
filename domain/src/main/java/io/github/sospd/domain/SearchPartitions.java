package io.github.sospd.domain;

import java.util.ArrayList;
import java.util.List;

/** Disjoint contiguous partitions of the cyclic seed space, with durable per-partition cursors. */
public final class SearchPartitions {
    private SearchPartitions() {}
    public record Lane(long start, long length, long scanned) {
        public Lane {
            if (start < 0 || start >= SeedCodec.TOTAL_SEEDS || length < 1 || length > SeedCodec.TOTAL_SEEDS
                    || scanned < 0 || scanned > length) throw new IllegalArgumentException("搜索分段进度无效");
        }
        public long nextSeed() { return (start + scanned) % SeedCodec.TOTAL_SEEDS; }
        public boolean exhausted() { return scanned == length; }
        public Lane advance() { return new Lane(start, length, scanned + 1); }
        public Lane retryLast() { return new Lane(start, length, Math.max(0, scanned - 1)); }
    }
    public record Checkpoint(List<Lane> lanes) {
        public Checkpoint {
            if (lanes == null || lanes.isEmpty() || lanes.size() > 4) throw new IllegalArgumentException("搜索分段数量无效");
            lanes = List.copyOf(lanes);
            long expected = lanes.get(0).start(), total = 0;
            for (Lane lane : lanes) {
                if (lane.start() != expected) throw new IllegalArgumentException("搜索分段不连续");
                expected = (expected + lane.length()) % SeedCodec.TOTAL_SEEDS;
                total += lane.length();
            }
            if (total != SeedCodec.TOTAL_SEEDS) throw new IllegalArgumentException("搜索分段未覆盖种子范围");
        }
        public long scanned() { return lanes.stream().mapToLong(Lane::scanned).sum(); }
    }
    public static Checkpoint create(long start, int workers) {
        if (start < 0 || start >= SeedCodec.TOTAL_SEEDS || workers < 1 || workers > 4)
            throw new IllegalArgumentException("搜索起点或并行数量无效");
        List<Lane> lanes = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            long from = SeedCodec.TOTAL_SEEDS * i / workers;
            long to = SeedCodec.TOTAL_SEEDS * (i + 1) / workers;
            lanes.add(new Lane((start + from) % SeedCodec.TOTAL_SEEDS, to - from, 0));
        }
        return new Checkpoint(lanes);
    }
}
