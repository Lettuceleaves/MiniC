int add(int left, int right) {
    return left + right;
}

int main() {
    int (*operation)(int, int) = add;
    return operation(5, 7);
}
