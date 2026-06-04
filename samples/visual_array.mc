// @visual root=values kind=array
int main() {
    int values[3];
    values[0] = 1;
    values[1] = 2;
    values[2] = values[0] + values[1];
    return values[2]; // @break
}
