package nus.dtn.app.broadcast;

public class ListModel {
    
    private String name="";
    private String lastLocation="";
    private String availability="";
    private String uniqueID="";
    private String link = "";
     
    /*********** Set Methods ******************/
    
   public void setID(String value){
	   this.uniqueID = value;
   }
     
    public void setName(String CompanyName)
    {
        this.name = CompanyName;
    }
     
    public void setlastLocation(String Image)
    {
        this.lastLocation = Image;
    }
     
    public void setAvail(String avail)
    {
        this.availability = avail;
    }

    public void setLink(String lin) { this.link = lin;}
     
    /*********** Get Methods ****************/
    
    public String getID(){
    	return uniqueID;
    }
    
    public String getName()
    {
        return this.name;
    }
     
    public String getLocation()
    {
        return this.lastLocation;
    }
 
    public String getAvailability()
    {
        return this.availability;
    }

    public String getLink() { return  this.link;}
}