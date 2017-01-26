import java.io.IOException;
import java.text.Collator;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

public class WordCount {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out
                    .println("Usage: [-b | -a | -h] [-is | -qs | -ms] <filename>\n");
            System.out.println("-b - Use an Unbalanced BST");
            System.out.println("-a - Use an AVL Tree");
            System.out.println("-h - Use a Hashtable\n");
            System.out.println("-is - Insertion Sort");
            System.out.println("-qs - Quick Sort");
            System.out.println("-ms - Merge Sort");
            return;
        }

        try {

            switch (args[1]) {
                case "-is":
                	insertionSort(countWords(args[0], args[2]));
                    break;
                case "-qs":
                	quickSort(countWords(args[0], args[2]));
                    break;
                case "-ms":
                	mergeSort(countWords(args[0], args[2]));
                    break;
                default:
                    System.out.println("Invalid second argument");
                    break;
            }
        } catch (IOException e) {
            System.out.println("ERROR: when parsing the file!!!");
            System.out.println(e.getMessage());
        }

    }

    public static DataCount<String>[] countWords(String dataStructure,
                                                 String filename) throws IOException {
        FileWordReader fileWordReader = new FileWordReader(filename);
        DataCounter<String> choice;
        String word;

        switch (dataStructure) {
            case "-b":
                choice = new BinarySearchTree<>();
                break;
            case "-a":
                choice = new AVLTree<>();
                break;
            case "-h":
                choice = new HashTable();
                break;
            default:
                choice = new BinarySearchTree<>();
                System.out
                        .println("Entered invalid data structure. BST by default.");
                break;
        }

        while ((word = fileWordReader.nextWord()) != null) {
            choice.incCount(word);
        }

        return choice.getCounts();
    }
    
    //Insertion Sort
    private static void insertionSort(DataCount<String>[] data) {
        final long startTime = System.currentTimeMillis();

        sortByInsertion(data);
        System.out.println("Sorted Frequency by Insertion Sort: ");
        printWordCount(data);


        final long endTime = System.currentTimeMillis();

        final long totalTime = endTime - startTime;
        System.out.println("Total amount of time taken: " + totalTime);
    }


    private static <E> void sortByInsertion(
            DataCount<E>[] counts) {
        for (int i = 1; i < counts.length; i++) {
            DataCount<E> x = counts[i];
            int j;
            for (j = i - 1; j >= 0; j--) {
                if (counts[j].count >= x.count) {
                    break;
                }
                counts[j + 1] = counts[j];
            }
            counts[j + 1] = x;
        }
    }
    
    //Quick Sort
    private static void quickSort(DataCount<String>[] data) {
        final long startTime = System.currentTimeMillis();

        sortByQuick(data,0, data.length-1);
        System.out.println("Sorted Frequency by Quick Sort: ");
        printWordCount(data);


        final long endTime = System.currentTimeMillis();

        final long totalTime = endTime - startTime;
        System.out.println("Total amount of time taken: " + totalTime);
    }
    
    public static <E> int compareTo(DataCount<E> a, DataCount<E> b) {
		if (a.count == b.count)
		{
			if (((String) a.data).compareTo((String) b.data) < 0)
			{
				return 1;
			}
			else if (((String) a.data).compareTo((String) b.data) > 0)
			{
				return -1;
			}
		}
		else { return a.count - b.count; }
		return 0;
	}
    
    private static <E extends Comparable<E>> void sortByQuick(
            DataCount<E>[] counts, int a, int b) {
    	if (a < b) {
            int i = a, j = b;
            DataCount<E> x = counts[(i + j) / 2];

            do {
                while (compareTo(counts[i],x) < 0) i++;
                while (compareTo(x,counts[j]) < 0) j--;

                if ( i <= j) {
                    DataCount<E> tmp = counts[i];
                    counts[i] = counts[j];
                    counts[j] = tmp;
                    i++;
                    j--;
                }

            } while (i <= j);

            sortByQuick(counts, a, j);
            sortByQuick(counts, i, b);
        }
    }
    
    //Merge Sort
    private static void mergeSort(DataCount<String>[] data) {
        final long startTime = System.currentTimeMillis();

        sortByMerge(data);
        System.out.println("Sorted Frequency by Merge Sort: ");
        printWordCount(data);


        final long endTime = System.currentTimeMillis();

        final long totalTime = endTime - startTime;
        System.out.println("Total amount of time taken: " + totalTime);
    }
    
    public static <E> void sortByMerge(DataCount<E>[] a)
	{
    	DataCount[] tmp = new DataCount[a.length];
		sortByMerge(a, tmp,  0,  a.length - 1);
	}


	private static <E> void sortByMerge(DataCount<E>[] a, DataCount<E>[] tmp, int left, int right)
	{
		if( left < right )
		{
			int center = (left + right) / 2;
			sortByMerge(a, tmp, left, center);
			sortByMerge(a, tmp, center + 1, right);
			merge(a, tmp, left, center + 1, right);
		}
	}


    private static <E> void merge(DataCount<E>[] a, DataCount<E>[] tmp, int left, int right, int rightEnd )
    {
        int leftEnd = right - 1;
        int k = left;
        int num = rightEnd - left + 1;

        while(left <= leftEnd && right <= rightEnd)
            if(compareTo(a[left],a[right]) <= 0)
                tmp[k++] = a[left++];
            else
                tmp[k++] = a[right++];

        while(left <= leftEnd)    
            tmp[k++] = a[left++];

        while(right <= rightEnd)  
            tmp[k++] = a[right++];
       
        for(int i = 0; i < num; i++, rightEnd--)
            a[rightEnd] = tmp[rightEnd];
    }

    private static void printWordCount(DataCount<String>[] data) {
        for (DataCount<String> dataCount : data) {
            System.out.format("%d %s\n", dataCount.count, dataCount.data);
        }
    }


}
