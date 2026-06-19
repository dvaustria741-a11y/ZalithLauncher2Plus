//
// Created by maks on 19.06.2023.
// Rewritten: pin to the WHOLE performance cluster (all cores sharing the
// highest max frequency) instead of a single core, so the OS scheduler can
// load-balance the JVM's many threads (render, GC, network, audio) across
// the full performance cluster rather than starving them onto one core.
// Also fixes two bugs from the original: sched_setaffinity() was called
// with CPU_SETSIZE (1024) as the byte-length argument instead of
// sizeof(cpu_set_t), causing an out-of-bounds read into kernel space, and
// strerror() was called on the syscall's return value instead of errno.
//

#define _GNU_SOURCE // we are GNU GPLv3

#include <linux/limits.h>
#include <unistd.h>
#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>
#include <sched.h>
#include <string.h>
#include <errno.h>

#include "bigcoreaffinity.h"

#define FREQ_MAX 256
#define MAX_CPU_CORES 32
// Cores within this fraction of the highest max-frequency core are treated
// as part of the same performance cluster (covers both "big+prime" setups
// like Dimensity 9000-class chips and plain big.LITTLE chips like Dimensity 920).
#define PERF_CLUSTER_THRESHOLD 0.90

static void bigcore_format_cpu_path(char* buffer, unsigned int cpu_core) {
    snprintf(buffer, PATH_MAX, "/sys/devices/system/cpu/cpu%i/cpufreq/cpuinfo_max_freq", cpu_core);
}

void bigcore_set_affinity() {
    char path_buffer[PATH_MAX];
    char freq_buffer[FREQ_MAX];
    char* discard;
    unsigned long core_freqs[MAX_CPU_CORES];
    unsigned int corecnt = 0;
    unsigned long max_freq = 0;

    memset(core_freqs, 0, sizeof(core_freqs));

    // Pass 1: read the max frequency of every core the kernel exposes
    while (corecnt < MAX_CPU_CORES) {
        bigcore_format_cpu_path(path_buffer, corecnt);
        int corefreqfd = open(path_buffer, O_RDONLY);
        if (corefreqfd == -1) break;

        ssize_t read_count = read(corefreqfd, freq_buffer, FREQ_MAX - 1);
        close(corefreqfd);
        if (read_count <= 0) break;
        freq_buffer[read_count] = 0;

        unsigned long core_freq = strtoul(freq_buffer, &discard, 10);
        core_freqs[corecnt] = core_freq;
        if (core_freq > max_freq) max_freq = core_freq;

        corecnt++;
    }

    if (corecnt == 0 || max_freq == 0) {
        printf("bigcore: failed to read any CPU frequency info, skipping affinity\n");
        return;
    }

    // Pass 2: select every core within the performance cluster threshold
    cpu_set_t bigcore_affinity_set;
    CPU_ZERO(&bigcore_affinity_set);
    unsigned int selected = 0;
    unsigned long threshold = (unsigned long)((double)max_freq * PERF_CLUSTER_THRESHOLD);

    for (unsigned int i = 0; i < corecnt; i++) {
        if (core_freqs[i] >= threshold) {
            CPU_SET(i, &bigcore_affinity_set);
            selected++;
        }
    }

    if (selected == 0) {
        printf("bigcore: no cores matched the performance cluster threshold, skipping\n");
        return;
    }

    printf("bigcore: pinning to %u performance core(s) out of %u total (cluster max freq %lu Hz)\n",
           selected, corecnt, max_freq);

    // sizeof(cpu_set_t) is the correct byte-length argument here, NOT CPU_SETSIZE
    // (CPU_SETSIZE is the bit-count, passing it as a byte-length over-reads the struct)
    int result = sched_setaffinity(0, sizeof(cpu_set_t), &bigcore_affinity_set);
    if (result != 0) {
        printf("bigcore: setting affinity failed: %s\n", strerror(errno));
    } else {
        printf("bigcore: forced current thread onto the performance cluster\n");
    }
}
