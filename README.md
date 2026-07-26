# toernooinl2googlecalendar
This application sync's the PBO competition calendars from toernooi.nl into individual google calendars per team.  

- __Step1__: using load_script/download.sh, a CSV file is downloaded from toernooi.nl and stored in data/tmp/matches.csv. The step required the correct credentials from PBO on toernooi.nl (load_script/secret_template.cfg)
- __Step2__: using the toernooinl2googlecalendar application, the CSV file is parsed and converted into XML, which is then used to update the google calendars of the teams. At the end, the logging will a list of team name with there corresponding google calendar URL. This step needs the correct credentials config/client_secret_template.json, calendar_secret_template.json) 
- __Step3__: A static webpage is available to display the google calendars of the teams. This step  

# Scheduling
- Github workflow .github/workflows/run-for-pbo.yml is executing the first two steps on a daily basis.

# Limitations
* Current Google API limitation: only "25 new calendars within a short period of time" can be created.
https://support.google.com/a/answer/2905486?hl=en


