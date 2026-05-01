struct Point {
    int x;
    int y;
};

int main() {
    struct Point point;
    point.x = 7;
    point.y = point.x + 5;
    return point.y;
}
