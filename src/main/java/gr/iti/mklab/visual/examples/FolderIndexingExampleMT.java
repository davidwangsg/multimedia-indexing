package gr.iti.mklab.visual.examples;

import gr.iti.mklab.visual.datastructures.AbstractSearchStructure;
import gr.iti.mklab.visual.datastructures.Linear;
import gr.iti.mklab.visual.vectorization.ImageVectorizationResult;
import gr.iti.mklab.visual.vectorization.ImageVectorizer;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Date;

/**
 * This class demonstrates multi-threaded VLAD+SURF vectorization and @{link Linear} indexing of the images in a given
 * folder.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class FolderIndexingExampleMT {

	public static final int maxIndexSize = 10000000;

	/**
	 * @param args
	 *            [0] folder that contains the image files
	 * @param args
	 *            [1] directory where the BDB index will be created
	 * @param args
	 *            [2] a comma separated list with full paths to the codebook files (also works for 1 codebook)
	 * @param args
	 *            [3] a comma separated list with the sizes of the codebooks
	 * @param args
	 *            [4] path to the file containing the pca projection matrix
	 * @param args
	 *            [5] projection length
	 * @param args
	 *            [6] number of processor threads to be used for vectorization (compute-intensive task)
	 * 
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		String imagesFolder = args[0];
		String indexFolder = args[1];
		String[] codebookFiles = args[2].split(",");
		String[] numCentroidsString = args[3].split(",");
		int[] numCentroids = new int[numCentroidsString.length];
		for (int i = 0; i < numCentroidsString.length; i++) {
			numCentroids[i] = Integer.parseInt(numCentroidsString[i]);
		}
		String pcaFile = args[4];
		int projectionLength = Integer.parseInt(args[5]);
		// suggestion for compute-intensive tasks by
		// http://codeidol.com/java/java-concurrency/Applying-Thread-Pools/Sizing-Thread-Pools/
		// int numVectorizationThreads = Runtime.getRuntime().availableProcessors() + 1;
		int numVectorizationThreads = Integer.parseInt(args[6]);

		// Initialize the vectorizer and the indexer
		ImageVectorizer vectorizer = new ImageVectorizer("surf", codebookFiles, numCentroids, projectionLength,
				pcaFile, numVectorizationThreads);
		String BDBEnvHome = indexFolder + "BDB_" + projectionLength;
		AbstractSearchStructure index = new Linear(projectionLength, maxIndexSize, false, BDBEnvHome, false, true, 0);

		// load the images
		File dir = new File(imagesFolder);
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				if (name.toLowerCase().endsWith(".jpeg") || name.toLowerCase().endsWith(".jpg")
						|| name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".gif"))
					return true;
				else
					return false;
			}
		};
		String[] files = dir.list(filter);

		// scheduling!!!
		System.out.println("Indexing started!");
		long start = System.currentTimeMillis();
		int submittedVectorizationsCounter = 0;
		int completedCounter = 0;
		int failedCounter = 0;

		while (true) {
			// if we can submit more tasks to the vectorizer then do it
			if (submittedVectorizationsCounter < files.length && vectorizer.canAcceptMoreTasks()) {
				// avoid extraction if an image is already indexed
				String imageName = files[submittedVectorizationsCounter];
				if (index.isIndexed(imageName)) { // this image has been already indexed
					System.out.println("image:" + imageName + " already indexed");
					completedCounter++;
				} else {
					vectorizer.submitImageVectorizationTask(imagesFolder, imageName);
				}
				submittedVectorizationsCounter++;
				System.out.println("Submitted vectorization tasks: " + submittedVectorizationsCounter + " image:"
						+ imageName);
			}

			// try to get an image vectorization result and to index the vector
			ImageVectorizationResult imvr = null;
			try {
				imvr = vectorizer.getImageVectorizationResult();
			} catch (Exception e) {
				failedCounter++;
				e.printStackTrace();
				System.out.println(e.toString());
				System.out.println("" + new Date() + ": " + failedCounter + " vectors failed");
			}
			if (imvr != null) {
				String name = imvr.getImageName();
				double[] vector = imvr.getImageVector();
				if (index.indexVector(name, vector)) {
					completedCounter++;
				} else {
					failedCounter++;
				}
				System.out.println("" + new Date() + ": " + completedCounter + " vectors indexed");
			}

			// check loop termination condition
			if ((completedCounter + failedCounter == files.length)) {
				System.out.println("Shutdown sequence has started!");
				vectorizer.shutDown();
				index.close();
				break;
			}
		}
		long end = System.currentTimeMillis();
		System.out.println("Total time: " + (end - start) + " ms");
		System.out.println(completedCounter + " indexing tasks completed!");
	}
}