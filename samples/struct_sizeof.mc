struct Point {
    int x;
    int y;
};

struct Padded {
    char c;
    int i;
};

int main() {
    struct Point p;
    int s1 = sizeof(p);
    int s2 = sizeof(struct Point);
    struct Padded pad;
    int s3 = sizeof(pad);
    return s1 + s2 + s3;
}
