int write_first(int *values) {
    values[0] = 7;
    values[1] = values[0] + 4;
    return values[1];
}

int main() {
    int values[2];
    write_first(values);
    return values[0] + values[1];
}
