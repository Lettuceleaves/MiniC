// @visual root=keys kind=array
// @visual root=values kind=array
// @visual root=states kind=array
int hashIndex(int key, int capacity) {
    return key % capacity;
}

int clearTable(int *keys, int *values, int *states, int capacity) {
    int i = 0;
    while (i < capacity) {
        keys[i] = 0;
        values[i] = 0;
        states[i] = 0;
        i = i + 1;
    }
    return 0;
}

int findSlot(int *keys, int *states, int capacity, int key) {
    int start = hashIndex(key, capacity);
    int step = 0;
    while (step < capacity) {
        int pos = (start + step) % capacity;
        if (states[pos] == 0) {
            return pos;
        }
        if (states[pos] == 1) {
            if (keys[pos] == key) {
                return pos;
            }
        }
        step = step + 1;
    }
    return -1;
}

int putOpen(int *keys, int *values, int *states, int capacity, int key, int value) {
    int slot = findSlot(keys, states, capacity, key);
    keys[slot] = key;
    values[slot] = value;
    states[slot] = 1;
    return 0;
}

int getOpen(int *keys, int *values, int *states, int capacity, int key) {
    int start = hashIndex(key, capacity);
    int step = 0;
    while (step < capacity) {
        int pos = (start + step) % capacity;
        if (states[pos] == 0) {
            return -1;
        }
        if (states[pos] == 1) {
            if (keys[pos] == key) {
                return values[pos];
            }
        }
        step = step + 1;
    }
    return -1;
}

int removeOpen(int *keys, int *values, int *states, int capacity, int key) {
    int start = hashIndex(key, capacity);
    int step = 0;
    while (step < capacity) {
        int pos = (start + step) % capacity;
        if (states[pos] == 0) {
            return 0;
        }
        if (states[pos] == 1) {
            if (keys[pos] == key) {
                states[pos] = 2;
                values[pos] = 0;
                return 0;
            }
        }
        step = step + 1;
    }
    return 0;
}

int resizeToEight(int *keys, int *values, int *states) {
    int oldKeys[16];
    int oldValues[16];
    int oldStates[16];
    int i = 0;
    while (i < 4) {
        oldKeys[i] = keys[i];
        oldValues[i] = values[i];
        oldStates[i] = states[i];
        i = i + 1;
    }
    clearTable(keys, values, states, 8);
    i = 0;
    while (i < 4) {
        if (oldStates[i] == 1) {
            putOpen(keys, values, states, 8, oldKeys[i], oldValues[i]);
        }
        i = i + 1;
    }
    return 0;
}

int main() {
    int keys[16];
    int values[16];
    int states[16];
    int capacity = 4;
    int score = 0;
    clearTable(keys, values, states, 16);
    putOpen(keys, values, states, capacity, 1, 100);
    putOpen(keys, values, states, capacity, 5, 500);
    putOpen(keys, values, states, capacity, 9, 900);
    score = score + getOpen(keys, values, states, capacity, 5);
    capacity = 8;
    resizeToEight(keys, values, states);
    putOpen(keys, values, states, capacity, 13, 1300);
    putOpen(keys, values, states, capacity, 2, 200);
    removeOpen(keys, values, states, capacity, 9);
    putOpen(keys, values, states, capacity, 10, 1000);
    score = score + getOpen(keys, values, states, capacity, 13);
    score = score + getOpen(keys, values, states, capacity, 9);
    return score + capacity; // @break
}
