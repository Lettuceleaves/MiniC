struct Point {
    int x;
    int y;
};

int write(struct Point *point) {
    point->x = 6;
    point->y = point->x + 7;
    return point->y;
}

int main() {
    struct Point point;
    return write(&point);
}
