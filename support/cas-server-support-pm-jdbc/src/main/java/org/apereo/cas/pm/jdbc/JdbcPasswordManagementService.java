package org.apereo.cas.pm.jdbc;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.apereo.cas.CipherExecutor;
import org.apereo.cas.authentication.Credential;
import org.apereo.cas.authentication.UsernamePasswordCredential;
import org.apereo.cas.authentication.support.password.PasswordEncoderUtils;
import org.apereo.cas.configuration.model.support.pm.PasswordManagementProperties;
import org.apereo.cas.pm.BasePasswordManagementService;
import org.apereo.cas.pm.PasswordChangeBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

/**
 * This is {@link JdbcPasswordManagementService}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
public class JdbcPasswordManagementService extends BasePasswordManagementService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcPasswordManagementService.class);

    private final JdbcTemplate jdbcTemplate;

    public JdbcPasswordManagementService(final CipherExecutor<Serializable, String> cipherExecutor,
                                         final String issuer,
                                         final PasswordManagementProperties passwordManagementProperties,
                                         final DataSource dataSource) {
        super(cipherExecutor, issuer, passwordManagementProperties);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }


    @Override
    public boolean changeInternal(final Credential credential, final PasswordChangeBean bean) {
        final UsernamePasswordCredential c = (UsernamePasswordCredential) credential;
        final PasswordEncoder encoder = PasswordEncoderUtils.newPasswordEncoder(properties.getJdbc().getPasswordEncoder());
        final String password = encoder.encode(bean.getPassword());
        final String oldPassword = (String)this.jdbcTemplate.queryForObject(properties.getJdbc().getSqlQueryOldPassword(), new Object[]{c.getId()}, String.class);
        if (password.equals(oldPassword)) {
            LOGGER.warn("cannot reset with old password");
            return false;
        }
        final int count = this.jdbcTemplate.update(properties.getJdbc().getSqlChangePassword(), password, c.getId());
        this.jdbcTemplate.update(properties.getJdbc().getSqlUnexpireAccount(), c.getId());
        final Date now = new Date();
        final SimpleDateFormat timeStampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        LOGGER.debug("last modified:" + timeStampFormat.format(now));
        final Timestamp nowTs = Timestamp.valueOf(timeStampFormat.format(now));
        this.jdbcTemplate.update(properties.getJdbc().getSqlUpdateUserTimestamp(), nowTs, c.getId());
        return count > 0;
    }

    @Override
    public String findEmail(final String username) {
        final String query = properties.getJdbc().getSqlFindEmail();
        final String email = this.jdbcTemplate.queryForObject(query, String.class, username);
        if (StringUtils.isNotBlank(email) && EmailValidator.getInstance().isValid(email)) {
            return email;
        }
        return null;
    }

    @Override
    public Map<String, String> getSecurityQuestions(final String username) {
        final String sqlSecurityQuestions = properties.getJdbc().getSqlSecurityQuestions();
        final Map<String, String> map = new LinkedHashMap<>();
        final List<Map<String, Object>> results = jdbcTemplate.queryForList(sqlSecurityQuestions, username);
        results.forEach(row -> {
            if (row.containsKey("question") && row.containsKey("answer")) {
                map.put(row.get("question").toString(), row.get("answer").toString());
            }
        });
        LOGGER.debug("Found [{}] security questions for [{}]", map.size(), username);
        return map;
    }
}
