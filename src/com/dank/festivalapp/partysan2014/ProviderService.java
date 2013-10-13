package com.dank.festivalapp.partysan2014;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.content.res.Resources;
import android.util.Log;

import com.dank.festivalapp.lib.ProviderServiceBase;
import com.dank.festivalapp.lib.DownloadFilesTask;
import com.dank.festivalapp.lib.News;
import com.dank.festivalapp.lib.Band;

public class ProviderService extends ProviderServiceBase {

	private static String partySanUrl = "http://www.party-san.de/";
	private DownloadFilesTask downloadFile = new DownloadFilesTask();
	
	public ProviderService() 
	{
		super();
	}

	@Override
	protected String getFestivalName() 
	{
		Resources res = getResources();		
		return res.getString(R.string.app_name);
	}

	/**
	 * download the band index page and parse urls, urls are temporary stored in band description.
	 * @param page
	 */
	@Override
	protected List<Band> getBands() {
		String page = downloadFile.downloadUrl(partySanUrl + "bands-2014/bands-2014/");
		
		Document doc = Jsoup.parse(page, "UTF-8");
		List<Band> bands = new ArrayList<Band>();		

		for (Element n : doc.getElementsByClass("psoabilling_listitem") )
		{
			Elements b =  n.getElementsByClass("psoabilling_bandname");			
			String name = b.text();
			Element link = b.select("a").first();
			String relHref = link.attr("href"); 

			Log.w("BANDNAME", name);

			Band band = new Band(name, relHref);
			bands.add(band);
		}
		
		return bands;
	}

	/**
	 * normalizes short version of flavors 
	 * (e.g. "Trash / Black Metal" to Trash Metal, Black Metal)
	 */
	private ArrayList<String> normalizeFlavor(String in)
	{
		ArrayList<String> res = new ArrayList<String>();

		if (in.contains("/"))
		{
			List<String> items = Arrays.asList(in.split("\\s*/\\s*"));

			String lastItem = items.get(items.size()-1).trim();
			String base = "";
			if (lastItem.contains(" "))
			{
				base = items.get(items.size()-1);				
				base = base.substring(base.indexOf(" ")).trim();
			}
			
			for (int i = 0; i < items.size()-1; i++)
				if(base.length() > 0)
					res.add( items.get(i).trim() + " " + base );
				else 
					res.add( items.get(i).trim() );
	
			res.add( lastItem );
		} else
		{
			res.add(in);
		}

		return res;
	}
	
	/**
	 * method to make some band detail actions, e.g. in case band
	 * details are on a seconds url
	 * @param band
	 * @return
	 */
	protected Band getBandDetailed(Band band)
	{
		Log.w("parseBandInfos", band.getBandname());
		String page = downloadFile.downloadUrl(partySanUrl + band.getDescription() );
	
		Document doc = Jsoup.parse(page, "UTF-8");

		Elements descElem =  doc.getElementsByClass("psoabilling_text");
		if (descElem != null)
		{
			String desc = descElem.select("p").text();
			band.setDescription(desc);
			Log.w("desc", desc);
		}

		Elements logoElem =  doc.getElementsByClass("psoabilling_bandlogodetail");
		if (logoElem != null)
		{
			String relLogoUrl = logoElem.select("img").first().attr("src");
			String relLogoUrlFiletype = relLogoUrl.substring(relLogoUrl.lastIndexOf("."));
			String logoFileName = band.getBandname() + "_logo" + relLogoUrlFiletype;

			if ( downloadFile.downloadUrlToFile(partySanUrl + relLogoUrl, logoFileName ) )
				band.setLogoFile(logoFileName);
		}

		Elements fotoElem =  doc.getElementsByClass("psoabilling_photo");
		if (fotoElem != null)
		{
			String relFotoUrl = fotoElem.select("img").first().attr("src");
			String relFotoUrlFiletype = relFotoUrl.substring(relFotoUrl.lastIndexOf("."));
			String fotoFileName =  band.getBandname() + relFotoUrlFiletype;

			if ( downloadFile.downloadUrlToFile(partySanUrl + relFotoUrl, fotoFileName) )
				band.setFotoFile(fotoFileName);
		}

		// extract describing elements from the list 
		Elements details =  doc.getElementsByClass("psoabilling_detailitemlist");
		if (details != null)
		{
			Element ul = details.select("ul").first();
			
			if (ul.select("a") != null)
				band.setUrl( ul.select("a").attr("href") );
			
			if (ul != null)
			{
				Elements lis = ul.select("li");

				for (Element li:lis)
				{
					String h = li.text();
					String d = "Updatedatum:";
					String f = "Stil:";

					if (h.contains(d))
					{
						try {
							Date date = new SimpleDateFormat("dd.MM.yyyy").parse( h.replace(d,"") );
							band.setAddDate(date);
						} catch (ParseException e) {
							e.printStackTrace();
						}
					}
					else if (h.contains(f))
					{
						h = h.replace(f, "").trim();				
						Log.w("Flavors", h);
						List<String> items = Arrays.asList(h.split("\\s*,\\s*"));
						for (String i:items)
						{
							ArrayList<String> fRes = normalizeFlavor(i.trim() );
							for(String i2:fRes)
								band.addFlavor(i2);
						}
					}
				}
			}
		}
		return band;
	}
	
	private String cleanUp(String s)
	{
		s = s.replaceAll("\\[mehr\\]", "");
		s = s.replaceAll(" +", " ");
		return s;
	}
		
	/**
	 * returns a list of all current News for this festival
	 * @return
	 */
	@Override
	protected List<News> getNewsShort() 
	{		
		List<News> newsList = new ArrayList<News>();
		String page = downloadFile.downloadUrl(partySanUrl + "news/");
		
		try {
			Document doc = Jsoup.parse(page, "UTF-8");

			for (Element n : doc.getElementsByClass("news-list-item") )
			{
				News news = new News();	
				String d =  n.getElementsByClass("news-list-date").text();
				Date date = new SimpleDateFormat("dd.MM.yyyy").parse(d);

				if (date.getYear() < 100)
					date.setYear(date.getYear() + 2000);
				news.setDate(date);

				String subject = n.getElementsByTag("a").text();
				news.setSubject( cleanUp( subject ) );

				String url = n.select("a").first().attr("href");
				news.setMessage(url);
				newsList.add(news);

				Log.d("parseAndUpdate", news.getDate().toString() + " " + news.getSubject() + " " + news.getMessage() );				
			}
		} catch (ParseException e) {		
			e.printStackTrace();
		}
		return newsList;
	}

	
	/**
	 * returns details to the given news, the used url was temporary stored as message
	 * an another url  
	 * @param news
	 * @return
	 */
	protected News getNewsDetailed(News news)
	{
		String newsDetailPage = downloadFile.downloadUrl(partySanUrl + news.getMessage());

		// preserve linebreaks
		// .replaceAll("<br />", "FestivalAppbr2n") - 
		Document docDetail = Jsoup.parse(newsDetailPage.replaceAll("<br />", "FestivalAppbr2n"), "UTF-8");
		Elements msg =  docDetail.getElementsByClass("news-single-item");
		
		msg.get(0).getElementsByClass("news-single-rightbox").remove(); // remove the date
		msg.get(0).getElementsByClass("psoanews_container").remove();
		msg.get(0).select("h1").remove(); // extra header not necessary, remove it
		
		String resMsg = msg.text().replaceAll("FestivalAppbr2n", "\n") ;
		news.setMessage(resMsg);

		return news;
	}
		
	
	private Date parseDate(String d)
	{
		try {
			return new SimpleDateFormat("dd.MM.yy HH:mm").parse( d );
		} catch (ParseException e1) {
			try {
				// there is a typo inside the page
				return new SimpleDateFormat("dd.MM.yy HH.mm").parse( d );
			} catch (ParseException e2) {
				e2.printStackTrace();
			}
		}
		return null;		
	}
		
	
	@Override
	protected List<BandGigTime> getRunningOrder() {
		
		String MAINSTAGE = "Mainstage";
		String TENTSTAGE = "Tentstage";
		
		Map<String, String> idMap = new HashMap<String, String>(); // HTML elements containing the times

		idMap.put("c671", MAINSTAGE );
		idMap.put("c672", MAINSTAGE );
		idMap.put("c673", MAINSTAGE );
		
		idMap.put("c680", TENTSTAGE);
		idMap.put("c677", TENTSTAGE);
		idMap.put("c694", TENTSTAGE);
		idMap.put("c676", TENTSTAGE);
		
		List<BandGigTime> allTimesList = new ArrayList<BandGigTime>();

		String page = downloadFile.downloadUrl(partySanUrl + "bands-2014/running-order-2014/");
		
		Document doc = Jsoup.parse(page, "UTF-8");
		for ( String id:idMap.keySet() ) 
		{
			Element eid = doc.getElementById( id );
			if(eid != null)
			{
				String day = eid.select("h1").text();
				day = day.substring(day.indexOf(" ") ).trim();

				Elements rows = eid.getElementsByTag("tr");
				for (Element row:rows)
				{
					Elements td = row.getElementsByTag("td");

					String time = td.get(0).text();
					String band = td.get(1).text();

					if (time.length() > 4)
					{
						BandGigTime pt = new BandGigTime();
						pt.bandname = band;
						pt.stage = idMap.get(id);

						String startTime = day + " " + time.substring(0, time.indexOf("-")).trim();
						pt.beginTime = parseDate(startTime);

						String endTime = day + " " + time.substring(time.indexOf("-")+1).trim();
						pt.endTime = parseDate(endTime);

						Log.d("parseGigs", 
								pt.stage + " " +
								pt.bandname +" " +
								pt.beginTime.toString() + " " + pt.endTime.toString()
								);
						allTimesList.add(pt);
					}
				}
			}
		}
		return allTimesList;	
	}
	
}
