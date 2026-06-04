// @visual root=ptr kind=struct-pointer
struct Point {
    int x;
    int y;
};

int main() {
    struct Point point;
    struct Point *ptr;
    point.x = 5;
    point.y = 6;
    ptr = &point;
    ptr->x = ptr->x + 1;
    return ptr->x + ptr->y; // @break
}
