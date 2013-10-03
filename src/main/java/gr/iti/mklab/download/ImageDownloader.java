package gr.iti.mklab.download;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * This class implements multi-threaded image downloading.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ImageDownloader {

	private ExecutorService downloadExecutor;

	private CompletionService<ImageDownload> pool;

	private int numPendingTasks;

	/**
	 * Used to limit the memory usage.
	 */
	private int maxNumPendingTasks;

	private boolean saveOriginal;

	private boolean saveThumb;

	private boolean followRedirects;

	public ImageDownloader(int numThreads) {
		downloadExecutor = Executors.newFixedThreadPool(numThreads);
		pool = new ExecutorCompletionService<ImageDownload>(downloadExecutor);
		numPendingTasks = 0;
		maxNumPendingTasks = 20;
		saveOriginal = false;
		saveThumb = true;
		followRedirects = false;
	}

	public ImageDownloader(int numThreads, int maxNumPendingTasks) {
		this(numThreads);
		this.maxNumPendingTasks = maxNumPendingTasks;
	}

	/**
	 * Gets an image download results from the pool.
	 * 
	 * @return the download result, or null in no results are ready
	 * @throws Exception
	 *             for a failed download task
	 */
	public ImageDownload getImageDownloadResult() throws Exception {
		Future<ImageDownload> future = pool.poll();
		if (future == null) { // no completed tasks in the pool
			return null;
		} else {
			try {
				ImageDownload imd = future.get();
				return imd;
			} catch (Exception e) {
				throw e;
			} finally {
				// in any case (Exception or not) the numPendingTask should be
				// reduced
				numPendingTasks--;
			}
		}
	}

	/**
	 * Gets an image download results from the pool, waiting if necessary.
	 * 
	 * @return the download result
	 * @throws Exception
	 *             for a failed download task
	 */
	public ImageDownload getImageDownloadResultWait() throws Exception {
		try {
			ImageDownload imd = pool.take().get();
			return imd;
		} catch (Exception e) {
			throw e;
		} finally {
			// in any case (Exception or not) the numPendingTask should be
			// reduced
			numPendingTasks--;
		}
	}

	/**
	 * Returns the number of tasks which have not been consumed.
	 * 
	 * @return
	 */
	public int getNumPendingTasks() {
		return numPendingTasks;
	}

	/**
	 * Returns true if the number of pending tasks is smaller than the maximum
	 * allowable number.
	 * 
	 * @return
	 */
	public boolean canAcceptMoreTasks() {
		if (numPendingTasks < maxNumPendingTasks) {
			return true;
		} else {
			return false;
		}
	}

	public void setFollowRedirects(boolean followRedirects) {
		this.followRedirects = followRedirects;
	}

	public void setSaveOriginal(boolean saveOriginal) {
		this.saveOriginal = saveOriginal;
	}

	public void setSaveThumb(boolean saveThumb) {
		this.saveThumb = saveThumb;
	}

	/**
	 * Shut the download executor down, waiting for up to 10 seconds for the
	 * remaining tasks to complete.
	 * 
	 * @throws InterruptedException
	 */
	public void shutDown() throws InterruptedException {
		downloadExecutor.shutdown();
		downloadExecutor.awaitTermination(10, TimeUnit.SECONDS);
	}

	/**
	 * Submits a new image download task.
	 * 
	 * @param URL
	 *            The url of the image
	 * @param id
	 *            The id of the image (used to name the image file after
	 *            download)
	 * @param downloadFolder
	 *            The folder where the image file is downloaded
	 */
	public void submitImageDownloadTask(String URL, String id,
			String downloadFolder) {
		Callable<ImageDownload> call = new ImageDownload(URL, id,
				downloadFolder, saveThumb, saveOriginal, followRedirects);
		pool.submit(call);
		numPendingTasks++;
	}

	/**
	 * This method exemplifies multi-threaded image download from a list of
	 * urls. It uses 5 download threads.
	 * 
	 * @param dowloadFolder
	 *            Full path to the folder where the images are downloaded
	 * @param urlsFile
	 *            Full path to the file that contains the ids and urls (space
	 *            separated) of the images (one per line)
	 * @param numUrls
	 *            The total number of urls to consider
	 * @param urlsToSkip
	 *            How many urls (from the top of the file to be skipped)
	 * @throws Exception
	 */
	public static void downloadFromUrlsFile(String dowloadFolder,
			String urlsFile, int numUrls, int urlsToSkip) throws Exception {
		long start = System.currentTimeMillis();
		int numThreads = 20;
		int maxNumPendingTasks = numThreads * 10;
		BufferedReader in = new BufferedReader(new FileReader(
				new File(urlsFile)));
		for (int i = 0; i < urlsToSkip; i++) {
			in.readLine();
		}
		ImageDownloader downloader = new ImageDownloader(numThreads,
				maxNumPendingTasks);
		int submittedCounter = 0;
		int completedCounter = 0;
		int failedCounter = 0;
		String line = "";
		while (true) {
			String url;
			String id = "";
			// if there are more task to submit and the downloader can accept
			// more tasks then submit
			while (submittedCounter < numUrls
					&& downloader.canAcceptMoreTasks()) {
				line = in.readLine();
				url = line.split("\\s+")[1];
				id = line.split("\\s+")[0];
				downloader.submitImageDownloadTask(url, id, dowloadFolder);
				submittedCounter++;
			}
			// if are submitted taks that are pending completion ,try to consume
			if (completedCounter + failedCounter < submittedCounter) {
				try {
					downloader.getImageDownloadResultWait();
					completedCounter++;
					System.out.println(completedCounter
							+ " downloads completed!");
				} catch (Exception e) {
					failedCounter++;
					System.out.println(failedCounter + " downloads failed!");
					System.out.println(e.getMessage());
				}
			}
			// if all tasks have been consumed then break;
			if (completedCounter + failedCounter == numUrls) {
				downloader.shutDown();
				in.close();
				break;
			}
		}
		long end = System.currentTimeMillis();
		System.out.println("Total time: " + (end - start) + " ms");
		System.out.println("Downloaded images: " + completedCounter);
		System.out.println("Failed images: " + failedCounter);
	}

	/**
	 * Calls the downloadFromUrlsFile.
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		String dowloadFolder = "images/";
		String urlsFile = "urls.txt";
		int numUrls = 1000;
		int urlsToSkip = 0;
		downloadFromUrlsFile(dowloadFolder, urlsFile, numUrls, urlsToSkip);
	}
}