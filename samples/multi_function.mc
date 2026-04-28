int add(int a, int b) {
    return a + b;
}

int mul(int a, int b) {
    return a * b;
}

int main() {
    return mul(add(1, 2), 4);
}
