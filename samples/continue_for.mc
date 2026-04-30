int main() {
    int sum = 0;
    for (int i = 0; i < 5; i = i + 1) {
        if (i == 2) {
            continue;
        }
        sum = sum + i;
    }
    return sum;
}
