SELECT slug,
       business_name,
       description,
       logo_url,
       brand_color
  FROM loyalty.tenants
 WHERE slug = :slug
   AND is_active
