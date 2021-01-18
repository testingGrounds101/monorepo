import requests
from zeep import Client

server_url = 'http://sakai.noodle-partners.com:8880'
agent = 'noodle'
username = agent + '-hedex-user'
password = 'noodle'

client = Client(server_url + '/sakai-ws/soap/login?wsdl')
session_id = client.service.login(username, password)

print('Session ID: %s' % session_id)

start_date = '2018-06-20'

if session_id:
    print("\nGetting engagement activity ...")
    r = requests.get(server_url + '/direct/hedex/Get_Retention_Engagement_EngagementActivity?RequestingAgent=' + agent + '&sessionid=' + session_id + '&startDate=' + start_date)
    print(r.json())

    print("\nGetting assignments ...")
    r = requests.get(server_url + '/direct/hedex/Get_Retention_Engagement_Assignments?RequestingAgent=' + agent + '&sessionid=' + session_id + '&startDate=' + start_date)
    print(r.json())
