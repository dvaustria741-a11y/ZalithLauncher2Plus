#ifndef ZALITHLAUNCHER_BIGCOREAFFINITY_H
#define ZALITHLAUNCHER_BIGCOREAFFINITY_H

/**
 * Pins the calling thread (and, since CPU affinity is inherited by threads
 * created afterwards, effectively the whole JVM process) onto every CPU
 * core within the device's highest-frequency performance cluster.
 *
 * Intended to be called once, early, before the JVM spins up its threads.
 */
void bigcore_set_affinity(void);

#endif //ZALITHLAUNCHER_BIGCOREAFFINITY_H
