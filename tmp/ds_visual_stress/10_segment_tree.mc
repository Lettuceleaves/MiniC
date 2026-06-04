// @visual root=tree kind=array
// @visual root=data kind=array
int build(int *data, int *tree, int node, int left, int right) {
    if (left == right) {
        tree[node] = data[left];
        return 0;
    }
    int mid = (left + right) / 2;
    build(data, tree, node * 2, left, mid);
    build(data, tree, node * 2 + 1, mid + 1, right);
    tree[node] = tree[node * 2] + tree[node * 2 + 1];
    return 0;
}

int query(int *tree, int node, int left, int right, int ql, int qr) {
    if (ql <= left) {
        if (right <= qr) {
            return tree[node];
        }
    }
    int mid = (left + right) / 2;
    int ans = 0;
    if (ql <= mid) {
        ans = ans + query(tree, node * 2, left, mid, ql, qr);
    }
    if (mid < qr) {
        ans = ans + query(tree, node * 2 + 1, mid + 1, right, ql, qr);
    }
    return ans;
}

int update(int *tree, int node, int left, int right, int pos, int value) {
    if (left == right) {
        tree[node] = value;
        return 0;
    }
    int mid = (left + right) / 2;
    if (pos <= mid) {
        update(tree, node * 2, left, mid, pos, value);
    } else {
        update(tree, node * 2 + 1, mid + 1, right, pos, value);
    }
    tree[node] = tree[node * 2] + tree[node * 2 + 1];
    return 0;
}

int main() {
    int data[8];
    int tree[32];
    int i = 0;
    int score = 0;
    while (i < 8) {
        data[i] = i + 1;
        tree[i] = 0;
        tree[i + 8] = 0;
        tree[i + 16] = 0;
        tree[i + 24] = 0;
        i = i + 1;
    }
    build(data, tree, 1, 0, 7);
    score = score + query(tree, 1, 0, 7, 0, 3);
    score = score + query(tree, 1, 0, 7, 2, 6);
    update(tree, 1, 0, 7, 3, 20);
    data[3] = 20;
    score = score + query(tree, 1, 0, 7, 0, 3);
    update(tree, 1, 0, 7, 6, 1);
    data[6] = 1;
    score = score + query(tree, 1, 0, 7, 4, 7);
    return score + tree[1]; // @break
}
