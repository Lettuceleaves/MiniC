int add(int left, int right) {
    return left + right;
}

int apply(int (*operation)(int, int), int left, int right) {
    return operation(left, right);
}

int main() {
    return apply(add, 5, 7);
}
