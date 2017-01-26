/**
 * Created by Jennifer Earley on 3/9/2016.
 */
public class AVLTree<E extends Comparable<E>> extends BinarySearchTree<E> implements DataCounter<E> {
    private int height;

    public AVLTree(){ }

    public void incCount(E data){
        if(overallRoot == null){
            overallRoot = new BSTNode(data);
        } else {
            BSTNode currentNode = overallRoot;
            while (true) {

                // compare the data to be inserted with the data at the current
                // node
                int cmp = data.compareTo(currentNode.data);

                if (cmp == 0) {
                    // current node is a match
                    currentNode.count++;
                    return;
                } else if (cmp < 0) {
                    // new data goes to the left of the current node
                    if (currentNode.left == null) {
                        currentNode.left = new BSTNode(data);
                        return;
                    }
                    currentNode = currentNode.left;
                } else {
                    // new data goes to the right of the current node
                    if (currentNode.right == null) {
                        currentNode.right = new BSTNode(data);
                        return;
                    }
                    currentNode = currentNode.right;
                }
            }
        }
        height = calcHeight(overallRoot);
        if(!isBalanced(overallRoot)){
            rebalance(overallRoot);
        }

    }

    public boolean isBalanced(BSTNode a){
        int check = calcHeight(a.left) - calcHeight(a.right);
        if(check > 1 || check < -1) {
            return false;
        } else {
            return true;
        }
    }

    public void rebalance(BSTNode b){
        if(whichSide(b).equals("right")){
            if(calcHeight(b.right.left) > calcHeight(b.right.right)){
                doubleLeftR(b);
            } else if(calcHeight(b.right.left) < calcHeight(b.right.right)){
                leftRotate(b);
            }
        } else if(whichSide(b).equals("left")){
            if(calcHeight(b.left.left) < calcHeight(b.left.right)){
                doubleRightR(b);
            } else if(calcHeight(b.left.left) > calcHeight(b.left.right)){
                rightRotate(b);
            }
        }
    }

    public String whichSide(BSTNode c){
        int check = calcHeight(c.left) - calcHeight(c.right);
        if(check > 1){
            return "left";
        } else {
            return "right";
        }
    }

    // left child rotates up to where c is
    public void rightRotate(BSTNode c){
        BSTNode r = c.left;
        c.left = r.right;
        r.right = c;
        overallRoot = r;
    }

    //right child rotates up to where c is
    public void leftRotate(BSTNode c){
        BSTNode r = c.right;
        c.right = r.left;
        r.left = c;
        overallRoot = r;
    }

    public void doubleLeftR(BSTNode c){
        rightRotate(c.right);
        leftRotate(c);
    }

    public void doubleRightR(BSTNode c){
        leftRotate(c.left);
        rightRotate(c);
    }

    public int calcHeight(BSTNode b){
        if(b == null){
            return -1;
        } else {
            return Math.max(calcHeight(b.left), calcHeight(b.right)) + 1;
        }
    }

    public int getSize() {
        return size;
    }

    public int getHeight(){
        return height;
    }

    public DataCount<E>[] getCounts() {
        @SuppressWarnings("unchecked")
        DataCount<E>[] counts = new DataCount[size];
        if (overallRoot != null)
            traverse(overallRoot, counts, 0);
        return counts;
    }
}
