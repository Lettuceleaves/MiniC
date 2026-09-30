namespace Counter {
    int value = 2;
    int add(int amount) {
        value += amount;
        return value;
    }
}
int main() {
    Counter::value = 5;
    int result = Counter::add(3);
    return result;
}
