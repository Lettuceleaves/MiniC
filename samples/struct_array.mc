struct Point {
    int x;
    int y;
};

int main() {
    struct Point arr[3];
    arr[0].x = 10;
    arr[0].y = 20;
    arr[1].x = 30;
    arr[1].y = 40;
    arr[2].x = 50;
    arr[2].y = 60;

    int sum = arr[0].x + arr[1].y + arr[2].x;
    return sum;
}
