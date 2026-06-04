// @visual root=matrix kind=matrix rows=2 columns=2
int main() {
    int matrix[4];
    matrix[0] = 1;
    matrix[1] = 2;
    matrix[2] = 3;
    matrix[3] = matrix[0] + matrix[2];
    return matrix[3]; // @break
}
