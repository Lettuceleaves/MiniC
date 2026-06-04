// @visual root=grid kind=struct-matrix rows=2 columns=2 fields=x,y
struct Point {
    int x;
    int y;
};

int main() {
    struct Point grid[4];
    grid[0].x = 1;
    grid[0].y = 2;
    grid[1].x = 3;
    grid[1].y = 4;
    grid[2].x = 5;
    grid[2].y = 6;
    grid[3].x = 7;
    grid[3].y = 8;
    return grid[0].x + grid[3].y; // @break
}
