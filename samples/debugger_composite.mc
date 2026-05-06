// @visual array name=buckets kind=hash_bucket_array root=value length=4 label=value
// @visual-node graph=chains id=0 label=bucket0
// @visual-node graph=chains id=1 label=node1
// @visual-edge graph=chains from=0 to=1 label=next directed=true
// @visual composite name=hash kind=hash_table
int main() {
    int value = 7;
    value = value + 1;
    return value;
}
