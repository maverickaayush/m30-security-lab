/* Offline syscall-contract tests. Never opens a Bluetooth socket. */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#define PHONE_HCI_PROBE_TEST
#include "../host/phone_hci_probe.c"

static int scenario, downs, closes, writes, filters;
static long syscall6(long n, long a0, long a1, long a2,
                     long a3, long a4, long a5)
{
    (void)a5;
    switch (n) {
    case 198: assert(a0 == 31 && a1 == (3 | 04000) && a2 == 1); return 8;
    case 200: return 0;
    case 29:
        assert(a0 == 8 && a2 == 0); /* scalar index, never &dev */
        if ((unsigned long)a1 == HCIDEVUP)
            return scenario == 1 ? -114 : scenario == 2 ? -19 : 0;
        assert((unsigned long)a1 == HCIDEVDOWN);
        downs++;
        return scenario == 8 ? -5 : 0;
    case 208: {
        const unsigned int *f = (const unsigned int *)a3;
        assert(a1 == 0 && a2 == 2 && a4 == 16);
        assert(f[0] == 16 && f[1] == (1U << 14) && f[2] == 0);
        assert(*(const u16 *)(a3 + 12) == 0x1001);
        filters++;
        return scenario == 9 ? -22 : 0;
    }
    case 64:
        if (a0 == 1) return a2;
        assert(a0 == 8 && filters == 1 && a2 == 4);
        assert(memcmp((void *)a1, "\1\1\20\0", 4) == 0);
        writes++;
        return scenario == 3 ? -5 : a2;
    case 73: {
        const long *ts = (const long *)a2;
        assert(a1 == 1 && ts[0] == 3 && ts[1] == 0 && a3 == 0 && a4 == 0);
        ((struct pollfd_local *)a0)->revents = POLLIN;
        return scenario == 4 ? 0 : scenario == 5 ? -4 : 1;
    }
    case 63: {
        const u8 response[15] = {4,14,12,1,1,0x10,0,8,0,0,8,0,0,0,0};
        memcpy((void *)a1, response, sizeof(response));
        if (scenario == 7) ((u8 *)a1)[6] = 1;
        if (scenario == 10) ((u8 *)a1)[4] = 2;
        return scenario == 6 ? -11 : scenario == 11 ? 7 : 15;
    }
    case 57: closes++; return 0;
    default: assert(!"unexpected syscall"); return -1;
    }
}

int main(void)
{
    for (scenario = 0; scenario < 12; scenario++) {
        downs = closes = writes = filters = 0;
        assert(probe() == (scenario == 0 ? 0 : 1));
        assert(closes == 1);
        assert(downs == (scenario == 1 || scenario == 2 ? 0 : 1));
        if (scenario == 1 || scenario == 2 || scenario == 9) assert(writes == 0);
    }
    puts("PASS: 12 mocked syscall/response/cleanup cases; no hardware accessed");
    return 0;
}
