extern int printf(char *format, ...);

int inc(int value) {
    return value + 1;
}

int main() {
    int value = 1;
    value = inc(value);
    printf("debug=%d\n", value);
    return value;
}
