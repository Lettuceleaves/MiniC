struct Point {
    int x;
    int y;
};

struct Point makePoint(int x, int y) {
    struct Point p;
    p.x = x;
    p.y = y;
    return p;
}

int sumPoint(struct Point p) {
    p.x = 999;
    return p.x + p.y;
}

int main() {
    struct Point p = makePoint(10, 20);
    int sum1 = sumPoint(p);
    int unchanged = p.x + p.y;
    return unchanged;
}
