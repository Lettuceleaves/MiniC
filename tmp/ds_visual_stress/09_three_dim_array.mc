// @visual root=cube kind=array
int index3(int x, int y, int z) {
    return x * 9 + y * 3 + z;
}

int set3(int *cube, int x, int y, int z, int value) {
    cube[index3(x, y, z)] = value;
    return 0;
}

int get3(int *cube, int x, int y, int z) {
    return cube[index3(x, y, z)];
}

int fillCube(int *cube) {
    int x = 0;
    while (x < 3) {
        int y = 0;
        while (y < 3) {
            int z = 0;
            while (z < 3) {
                set3(cube, x, y, z, x * 100 + y * 10 + z);
                z = z + 1;
            }
            y = y + 1;
        }
        x = x + 1;
    }
    return 0;
}

int main() {
    int cube[27];
    int score = 0;
    fillCube(cube);
    score = score + get3(cube, 0, 0, 0);
    score = score + get3(cube, 1, 2, 0);
    set3(cube, 2, 2, 2, 999);
    score = score + get3(cube, 2, 2, 2);
    set3(cube, 1, 1, 1, 555);
    score = score + get3(cube, 1, 1, 1);
    return score; // @break
}
