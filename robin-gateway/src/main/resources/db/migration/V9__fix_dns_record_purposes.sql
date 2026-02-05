UPDATE dns_records 
SET purpose = 'SERVICE_DISCOVERY' 
WHERE purpose = 'NS' 
  AND (type = 'SRV' OR name LIKE 'autoconfig%' OR name LIKE 'autodiscover%');
