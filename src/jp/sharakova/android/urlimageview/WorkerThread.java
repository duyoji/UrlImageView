package jp.sharakova.android.urlimageview;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.URL;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public final class WorkerThread extends Thread {
	private final Channel channel;

	public WorkerThread(String name, Channel channel) {
		super(name);
		this.channel = channel;
	}

	@Override
	public void run() {
		while (true) {
			Request request = channel.takeRequest();
			request.setStatus(Request.Status.LOADING);							
			SoftReference<Bitmap> image = ImageCache.getImage( request.getCacheDir(), request.getUrl() );
			if (image == null || image.get() == null) {
				image = getImage(request);				
				if (image != null && image.get() != null) {
					ImageCache.saveBitmap(request.getCacheDir(), request.getUrl(), image.get());
				}
			}			
			request.setStatus(Request.Status.LOADED);
			request.getRunnable().run();
		}
	}
	
	private SoftReference<Bitmap> getImage(Request request) {
		try {
			return new SoftReference<Bitmap>(getBitmapFromURL(request));
		} catch (Exception e) {
			e.printStackTrace();
			return null;			
		} catch (OutOfMemoryError e) {
			e.printStackTrace();
			return null;
		}
	}
	
	
	private Bitmap getBitmapFromURL(Request request) throws IOException {
		String fileName = ImageCache.getFileName(request.getUrl());
		File localFile = new File(request.getCacheDir(), fileName);
		FileOutputStream fos = null;
		
		HttpURLConnection con = null;
		InputStream in = null;

		try {
			URL url = new URL(request.getUrl());
			con = (HttpURLConnection) url.openConnection();
			con.setUseCaches(true);
			con.setRequestMethod("GET");
			con.setReadTimeout(500000);
			con.setConnectTimeout(50000);
			con.connect();
			in = con.getInputStream();
			
			//ImageCache#saveBitmap内で行っているbitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)が重いためこちらに変更
			byte[] buf = new byte[1024];				
			int len = 0;
			fos = new FileOutputStream(localFile);
			while((len = in.read(buf)) > -1){
				fos.write(buf, 0, len);
			}				
			fos.flush();
		} finally {
			try {
				if (con != null)
					con.disconnect();
				if (in != null)
					in.close();
				if (fos != null)
					fos.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return BitmapFactory.decodeFile(localFile.getPath());
	}

}