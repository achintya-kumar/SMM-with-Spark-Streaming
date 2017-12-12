# Monitor server setup

* To use the barefoot monitor server for tracking follow the below steps
  - Generate self signed certificate and private key for HTTPS server using openssl
  - OpenLDAP installation
  - LDAP Server Administration
  - Adding users via phpldapadmin

### Generating self signed certificate and private key using openssl
* Run below commands in the terminal in barefoot's monitor module```barefoot/util/monitor/```
  - ```openssl genrsa -out key.pem```
  - ```openssl req -new -key key.pem -out csr.pem```
  - ```openssl x509 -req -days 9999 -in csr.pem -signkey key.pem -out cert.pem```
  - ```rm csr.pem```

### OpenLDAP installation
* Follow below steps to install OpenLDAP
  - ```sudo apt-get install slapd ldap-utils```
  - Enter a password and re-enter the same password
  - ```sudo dpkg-reconfigure slapd```
  - Select **NO** and press **ENTER**
  - Enter DNS name, ex: ldap.com
  - Enter Organization Name, ex: xyzcompany
  - Enter LDAP admin password
  - Choose default backend database
  - Next select **NO** under LDAPv2 Protocol
  - Test openLDAP installation with the command ```ldapsearch -x```
  - Metadata related to LDAP will be returned

### LDAP Server Administration
* Installing phpldapadmin to use web interface to access LDAP Server
  - ```sudo apt-get install phpldapadmin```
  - ```sudo nano /etc/phpldapadmin/config.php```
  - Once the phpldapadmin config is opened edit the following values by searching the property names
    - Set LDAP server names

      * ```$servers->setValue('server','name', My LDAP Server');```

    - Set LDAP server IP address

      * ```$servers->setValue('server','host','IP-Address');```
    - Set Server domain name

      * ```$servers->setValue('server','base',array('dc=ldap,dc=com'));```
    - Set Server domain name again

      * ```$servers->setValue('login','bind_id','cn=admin,dc=ldap,dc=com');```
    - Set custom appearance to **true**

      * ```$config->custom->appearance['hide_template_warning'] = true;```
    - Save the file and exit
    - Restart apache2 server
      * ```sudo systemctl restart apache2```

### Add users via phpldapadmin web interface
- Follow the below steps to create user accounts
  - Open the link ```http://ip-address/phpldapadmin``` in a web browser
  - Use the password that is given during LDAP server installation
  - Click ***+*** at the root
  - Click ***create new entry here***
  - Click ***Generic : Organizational Unit***
  - Give a desired name and click ***Create Object***
  - Then click ***Commit***
  - Click on newly created Organisational Unit and click ***Create a child entry*** on the right side window
  - Then click ***Generic Posix Group***
  - Give a desired group name and click ***Create Object***
  - Then click ***Commit***
  - Now select the above created group and click ***Create a child entry***
  - Click ***Generic : User Account***
  - Enter a ***Common Name***
  - Enter ***First Name***
  - Select a ***GID Number*** from the dropdown list
  - Give a path in ***Home Directory*** field
  - Enter ***Last Name***
  - Select ***/bin/sh*** from ***Login Shell*** dropdown list
  - Enter the ***Password*** and re-enter to confirm it
  - Click ***Create Object***
  - Once the table appears check the given values and click ***Commit***
- The Monitor server can be accessed with the above created ***Username*** and ***Password***

[Reference] https://www.youtube.com/watch?v=MMtZzVW310g&feature=youtu.be
