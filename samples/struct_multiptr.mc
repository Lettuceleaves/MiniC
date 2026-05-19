struct Point {
    int x;
    int y;
};

int main() {
    struct Point p;
    p.x = 7;
    p.y = 13;

    struct Point *pp = &p;
    pp->x = 20;

    struct Point **ppp = &pp;
    (*ppp)->y = 30;

    return p.x + p.y;
}
