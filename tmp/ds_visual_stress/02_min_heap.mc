// @visual root=heap kind=heap
int less(int a, int b) {
    if (a < b) {
        return 1;
    }
    return 0;
}

int swap(int *heap, int a, int b) {
    int t = heap[a];
    heap[a] = heap[b];
    heap[b] = t;
    return 0;
}

int siftUp(int *heap, int index) {
    int cur = index;
    while (cur > 0) {
        int parent = (cur - 1) / 2;
        if (less(heap[cur], heap[parent]) == 0) {
            return 0;
        }
        swap(heap, cur, parent);
        cur = parent;
    }
    return 0;
}

int siftDown(int *heap, int size, int index) {
    int cur = index;
    while (cur < size) {
        int left = cur * 2 + 1;
        int right = cur * 2 + 2;
        int best = cur;
        if (left < size) {
            if (less(heap[left], heap[best]) != 0) {
                best = left;
            }
        }
        if (right < size) {
            if (less(heap[right], heap[best]) != 0) {
                best = right;
            }
        }
        if (best == cur) {
            return 0;
        }
        swap(heap, cur, best);
        cur = best;
    }
    return 0;
}

int heapPush(int *heap, int *size, int value) {
    heap[*size] = value;
    siftUp(heap, *size);
    *size = *size + 1;
    return 0;
}

int heapPop(int *heap, int *size) {
    int ans = heap[0];
    *size = *size - 1;
    heap[0] = heap[*size];
    heap[*size] = 0;
    siftDown(heap, *size, 0);
    return ans;
}

int heapUpdate(int *heap, int size, int index, int value) {
    int old = heap[index];
    heap[index] = value;
    if (value < old) {
        siftUp(heap, index);
    } else {
        siftDown(heap, size, index);
    }
    return 0;
}

int main() {
    int heap[16];
    int size = 0;
    int sum = 0;
    heapPush(heap, &size, 9);
    heapPush(heap, &size, 4);
    heapPush(heap, &size, 7);
    heapPush(heap, &size, 1);
    heapPush(heap, &size, 6);
    sum = sum + heapPop(heap, &size);
    heapPush(heap, &size, 2);
    heapUpdate(heap, size, 2, 0);
    sum = sum + heapPop(heap, &size);
    sum = sum + heapPop(heap, &size);
    heapPush(heap, &size, 3);
    heapPush(heap, &size, 8);
    return sum + heap[0] + size; // @break
}
