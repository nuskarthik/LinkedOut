package nus.dtn.app.broadcast;

public class ListModel {
    
    private  String name="";
    private  String lastLocation="";
    private  String availability="";
     
    /*********** Set Methods ******************/
     
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
     
    /*********** Get Methods ****************/
     
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
}