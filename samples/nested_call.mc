int inc(int x) {
    return x + 1;
}

int add(int a, int b) {
    return a + b;
}

int main() {
    return add(inc(2), add(3, 4));
}
