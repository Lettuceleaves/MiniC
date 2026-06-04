// @visual root=point kind=struct fields=x,y
struct Point {
    int x;
    int y;
};

int main() {
    struct Point point;
    point.x = 3;
    point.y = 4;
    return point.x + point.y; // @break
}
