// @visual root=points kind=struct-array fields=x,y
struct Point {
    int x;
    int y;
};

int main() {
    struct Point points[2];
    points[0].x = 1;
    points[0].y = 2;
    points[1].x = 3;
    points[1].y = 4;
    return points[0].x + points[1].y; // @break
}
