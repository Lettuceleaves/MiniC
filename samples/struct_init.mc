struct Point {
    int x;
    int y;
};

struct Rect {
    int left;
    int top;
    int right;
    int bottom;
};

int main() {
    struct Point p = {10, 20};
    struct Rect r = {1, 2, 3, 4};
    return p.x + p.y + r.left + r.right;
}
