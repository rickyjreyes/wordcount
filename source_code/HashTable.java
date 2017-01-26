import java.util.ArrayList;

/**
 * TODO Replace this comment with your own.
 *
 * Stub code for an implementation of a DataCounter that uses a hash table as
 * its backing data structure. We included this stub so that it's very clear
 * that HashTable works only with Strings, whereas the DataCounter interface is
 * generic.  You need the String contents to write your hashcode code.
 */


public class HashTable implements DataCounter<String> {

    //hamlet has ~34205 words
    //atlantis has ~17658 words
    private int size;
    private int tableSize = 523;
    private HashNode[] table;
    private ArrayList<String> keys;



    public HashTable(){
        table = new HashNode[tableSize];
        size = 0;
        keys = new ArrayList<>();
    }

    private int hash(String key){
        int hashCode = 7;
        for(int i = 0; i < key.length(); i++) {
            hashCode = (hashCode * 23) + key.charAt(i);
        }

        hashCode = hashCode % tableSize;

        if(hashCode < 0){
            hashCode = hashCode + tableSize;
        }

        return hashCode;
    }

    public class HashNode{
        String word;
        int count;
        HashNode next;

        public HashNode(String w){
            word = w;
            count = 1;
            next = null;
        }
    }

    /** {@inheritDoc} */
    public DataCount<String>[] getCounts() {
        ArrayList<DataCount> listCounts = new ArrayList<>();
        for(int i = 0; i < keys.size(); i++){
            int index = hash(keys.get(i));
            HashNode currentNode = table[index];
            while(currentNode != null) {
                listCounts.add(new DataCount(currentNode.word, currentNode.count));
                currentNode = currentNode.next;
            }
        }

        DataCount<String>[] counts = new DataCount[listCounts.size()];
        for(int i = 0; i < listCounts.size(); i++){
            counts[i] = listCounts.get(i);
        }
        return counts;
    }

    /** {@inheritDoc} */
    public int getSize() {
        return size;
    }

    /** {@inheritDoc} */
    public void incCount(String data) {
        int place = hash(data);
        if(table[place] == null){
            table[place] = new HashNode(data);
            size++;
            keys.add(data);
        } else {
            HashNode currentNode = table[place];
            HashNode nextNode = currentNode.next;
            while(nextNode != null){
                //System.out.println(data);
                //System.out.println("loop");
                if(currentNode.word.equals(data)){
                    currentNode.count++;
                    return;
                }
                currentNode = nextNode;
                nextNode = nextNode.next;
            }
            currentNode.next = new HashNode(data);
            size++;
        }
    }
}
