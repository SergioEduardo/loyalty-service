SELECT slug,
       business_name,
       description,
       logo_url,
       brand_color,
       points_goal,
       reward_text,
       cooldown_seconds,
       undo_window_seconds
  FROM loyalty.tenants
 WHERE id = :tenantId
   AND is_active
