#!/usr/bin/env python3
"""Guarded opt-in seeded RNG for pinned Forge and Lab subprocess."""
from pathlib import Path
import sys
forge=Path(sys.argv[1]); lab=Path(sys.argv[2])
s=forge.read_text()
old='    private static Random random = new SecureRandom();'
new='''    private static Random random = initialRandom();

    private static Random initialRandom() {
        String seed = System.getProperty("forge.lab.replaySeed");
        if (seed == null || seed.isEmpty()) {
            return new SecureRandom();
        }
        long parsed = Long.parseLong(seed);
        System.out.println("FORGE_REPLAY_SEED=" + parsed);
        return new Random(parsed);
    }'''
assert s.count(old)==1,"Forge RNG anchor changed"
forge.write_text(s.replace(old,new,1))
s=lab.read_text()
old='        cmd.add("-Dfile.encoding=UTF-8");'
new='''        cmd.add("-Dfile.encoding=UTF-8");
        String replaySeed = System.getenv("FORGE_LAB_REPLAY_SEED");
        if (replaySeed != null && !replaySeed.isBlank()) {
            Long.parseLong(replaySeed);
            cmd.add("-Dforge.lab.replaySeed=" + replaySeed);
            System.out.println("FORGE_REPLAY_SEED_REQUESTED=" + replaySeed);
        }'''
assert s.count(old)==1,"Lab JVM argument anchor changed"
lab.write_text(s.replace(old,new,1))
print("Installed opt-in replay seed plumbing (default SecureRandom preserved)")
