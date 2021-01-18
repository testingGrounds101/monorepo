require 'rubygems'
require 'sinatra/base'
require 'java'
require 'json'
require 'set'

PAGE_SIZE = 10

module DevelopmentMode

  def with_db_connection
    conn = java.sql.DriverManager.getConnection("(jdbc URL here)")
    with_open(conn) do
      yield(conn)
    end
  end


  def get_current_user
    "admin"
  end


  def user_exists?(user)
    true
  end


  def user_is_admin?(user)
    true
  end

end


module ProductionMode

  def self.included(base)
    @@sqlService = org.sakaiproject.component.cover.ComponentManager.get("org.sakaiproject.db.api.SqlService")
    @@userDirectoryService = org.sakaiproject.component.cover.ComponentManager.get("org.sakaiproject.user.api.UserDirectoryService")
  end


  def with_db_connection
    conn = @@sqlService.borrow_connection

    begin
      yield(conn)
    ensure
      @@sqlService.return_connection(conn)
    end
  end


  def get_current_user
    @@userDirectoryService.current_user.eid
  end


  def user_is_admin?(user)
    user = @@userDirectoryService.get_user_by_eid(user)

    admin_site = org.sakaiproject.component.cover.ServerConfigurationService.getString("nyu.serviceteam.adminSite")
    service_team = org.sakaiproject.component.cover.ServerConfigurationService.getString("nyu.serviceteam.role")

    admin_site_list = java.util.Vector.new
    admin_site_list.add(admin_site)

    authzGroupService = org.sakaiproject.component.cover.ComponentManager.get("org.sakaiproject.authz.api.AuthzGroupService")
    roles = authzGroupService.getUserRoles(user.getId(), admin_site_list)

    roles[admin_site] == service_team || roles[admin_site] == "admin"
  end


  def params
    SakaiParams.new(super, env['java.servlet_request'])
  end


  def user_exists?(user)
    begin
      @@userDirectoryService.get_user_by_eid(user)
    rescue org.sakaiproject.user.api.UserNotDefinedException
      false
    end
  end
end


class AuthMiddleware

  def initialize(app)

    self.class.instance_eval do
      include ProductionMode
    end

    @app = app
  end

  def call(env)

    if get_current_user.nil? || !user_is_admin?(get_current_user)
      [403, {"Content-Type" => "text/plain"},
       ["Permission denied for user: #{get_current_user.inspect}"]]
    else
      @app.call(env)
    end
  end

end


# The Sakai Request Filter will have already nicked any POST form params and
# loaded them into a map.  Interrogate that map in addition to the params made
# available by Sinatra.
#
class SakaiParams

  def initialize(sinatra_params, servlet_request)
    @sinatra_params = sinatra_params
    @servlet_request = servlet_request
  end


  def merge(hash)
    @sinatra_params = @sinatra_params.merge(hash)
  end


  def [](key)
    servlet_map = @servlet_request.getParameterMap

    if servlet_map.containsKey("#{key}[]")
      # An array.  Grab all values
      servlet_map.get("#{key}[]").to_a
    else
      @servlet_request.getParameter(key.to_s) or
        @sinatra_params[key]
    end
  end

end


class Surrogates < Sinatra::Base

  if development?
    include DevelopmentMode
  else
    include ProductionMode
    use AuthMiddleware
  end

  include ERB::Util

  configure :development do |config|
    require 'sinatra/reloader'
    register Sinatra::Reloader
  end


  def with_open(obj)
    begin
      yield(obj)
    ensure
      obj.close
    end
  end


  def get_academic_sessions
    with_db_connection do |conn|
      stmnt = conn.prepare_statement("select code_4char, descr from nyu_t_acad_session" +
                                     " where current_flag = 'Y'" +
                                     " order by term_begin_dt asc")

      with_open(stmnt) do |stmnt|
        with_open(stmnt.execute_query) do |rs|
          sessions = Set.new
          while rs.next
            sessions << Hash[["CODE_4CHAR", "DESCR"].map {|col|
                               [col, rs.getString(col)]}]
          end

          sessions.to_a
        end
      end
    end
  end


  def get_courses_for(session, school, department, substring)
    with_db_connection do |conn|
      substring = substring.gsub(/[^a-zA-Z :]/, "_").upcase

      stmnt = conn.prepare_statement("select stem_name, cle_crseid, descr from nyu_t_course_catalog" +
                                     " where strm in (select strm from nyu_t_acad_session where code_4char = ?)" +
                                     "  AND acad_group = ? " +
                                     (department ? "  AND acad_org = ? " : "") +
                                     " AND (UPPER(stem_name) LIKE '%#{substring}%'" +
                                     "      OR UPPER(descr) LIKE '%#{substring}%')" +
                                     " order by stem_name")


      with_open(stmnt) do |stmnt|
        stmnt.setString(1, session)
        stmnt.setString(2, school)
        stmnt.setString(3, department) if department

        with_open(stmnt.execute_query) do |rs|
          courses = []
          while rs.next
            courses << "#{rs.getString('STEM_NAME')} -- #{rs.getString('DESCR')}"
          end

          courses
        end
      end
    end
  end


  def get_schools
    with_db_connection do |conn|
      stmnt = conn.prepare_statement("select distinct(acad_group) from nyu_t_course_catalog order by acad_group")

      with_open(stmnt) do |stmnt|
        rs = stmnt.execute_query

        schools = []
        while rs.next
          schools << rs.getString("ACAD_GROUP")
        end

        schools
      end
    end
  end


  def get_departments_for(session, school)
    with_db_connection do |conn|
      stmnt = conn.prepare_statement("select distinct(acad_org) from nyu_t_course_catalog where acad_group = ?" +
                                     " AND strm in (select strm from nyu_t_acad_session where code_4char = ?)" +
                                     " ORDER BY acad_org")

      with_open(stmnt) do |stmnt|
        stmnt.setString(1, school)
        stmnt.setString(2, session)

        departments = []
        with_open(stmnt.execute_query) do |rs|
          while rs.next
            departments << rs.getString(1)
          end
        end

        departments
      end
    end
  end


  def get_subjects_for(session, school, department)
    with_db_connection do |conn|
      sql = ("select distinct(subject) from nyu_t_course_catalog where acad_group = ?" +
             " AND strm in (select strm from nyu_t_acad_session where code_4char = ?)" +
             " %s" +
             " ORDER BY subject")

      sql = sprintf(sql, (department ? "AND acad_org = ?" : ""))

      stmnt = conn.prepare_statement(sql)

      with_open(stmnt) do |stmnt|
        stmnt.setString(1, school)
        stmnt.setString(2, session)
        stmnt.setString(3, department) if department

        subjects = []
        with_open(stmnt.execute_query) do |rs|
          while rs.next
            subjects << rs.getString(1)
          end
        end

        subjects
      end
    end
  end


  def stream_for(conn, session)
    stmnt = conn.prepare_statement("select strm from nyu_t_acad_session where code_4char = ?")

    with_open(stmnt) do |stmnt|
      stmnt.setString(1, session)

      with_open(stmnt.execute_query) do |rs|
        if rs.next
          return rs.getString(1)
        else
          raise "Failed to find stream for #{session}"
        end
      end
    end
  end


  def grant_courses_to_users(courses, users, school, session)
    with_db_connection do |conn|

      operator = get_current_user
      strm = stream_for(conn, session)

      begin
        conn.setAutoCommit(false)

        insert = conn.prepare_statement("insert into NYU_T_COURSE_ADMINS_ADHOC (stem_name, strm, netid, lastupddtm, operator_id, active)" +
                                        " VALUES (?, ?, ?, ?, ?, 'Y')")

        update = conn.prepare_statement("update NYU_T_COURSE_ADMINS_ADHOC set active = 'Y', operator_id = ?, lastupddtm = ? " +
                                        " where stem_name = ? AND strm = ? AND netid = ?")

        courses.each do |course|
          users.each do |user|
            begin
              # Try the insert.  If that fails because of an integrity constraint,
              # update the existing row instead.
              insert.setString(1, course)
              insert.setString(2, strm)
              insert.setString(3, user)
              insert.setDate(4, java.sql.Date.new(java.lang.System.currentTimeMillis))
              insert.setString(5, operator)

              insert.execute
            rescue java.sql.SQLException => e
              if (e.cause or e).getSQLState() =~ /^23/
                # Integrity constraint

                update.setString(1, operator)
                update.setDate(2, java.sql.Date.new(java.lang.System.currentTimeMillis))
                update.setString(3, course)
                update.setString(4, strm)
                update.setString(5, user)

                update.execute
              else
                raise e
              end
            end
          end
        end

        conn.commit()

      ensure
        insert.close
        update.close
        conn.rollback()
        conn.setAutoCommit(true)
      end
    end
  end


  def remove_entry(user, course)
    with_db_connection do |conn|
      begin
        conn.setAutoCommit(false)

        remove = conn.prepare_statement("update NYU_T_COURSE_ADMINS_ADHOC " +
                                        " set active = 'N' where" +
                                        " netid = ? AND stem_name = ?")

        with_open(remove) do |remove|
          remove.setString(1, user)
          remove.setString(2, course)
          remove.execute
          conn.commit()
        end
      ensure
        conn.rollback()
        conn.setAutoCommit(true)
      end
    end
  end


  def paginate(db, sql, page, page_size)

    if db == 'MySQL'
      offset = page_size * page

      "#{sql} LIMIT #{page_size + 1} OFFSET #{offset}"

    elsif db == 'Oracle'
      topn = (page_size * (page + 1)) + 1

      "select * from " +
        "(select rownum as n, sub.* from " +
        "  (select * from (#{sql}) where rownum <= #{topn}) sub) " +
        "where n > #{page * page_size}"

    else
      raise "Missing pagination SQL for database '#{db}'"
    end
  end


  def show_users(page, netid, course)

    with_db_connection do |conn|

      adhoc_admin_sql = ("select NETID, STEM_NAME" +
                         " from NYU_T_COURSE_ADMINS_ADHOC" +
                         " where active = 'Y'" +
                         " %s %s " +
                         " ORDER BY netid")

      adhoc_admin_sql = sprintf(adhoc_admin_sql,
                                (netid ? "AND lower(netid) = ?" : ""),
                                (course ? "AND lower(stem_name) = ?" : ""))

      stmnt = conn.prepare_statement(paginate(conn.getMetaData.getDatabaseProductName,
                                              adhoc_admin_sql, page, PAGE_SIZE))

      if netid && course
        stmnt.set_string(1, netid.downcase)
        stmnt.set_string(2, course.downcase)
      elsif netid
        stmnt.set_string(1, netid.downcase)
      elsif course
        stmnt.set_string(1, course.downcase)
      end

      result = []
      with_open(stmnt.execute_query) do |rs|
        while rs.next
          result << [rs.getString('NETID'), rs.getString('STEM_NAME')]
        end
      end

      {
        :rows => result.slice(0, PAGE_SIZE),
        :has_more => result.count > PAGE_SIZE
      }
    end
  end


  def bulk_search(text)
    possible_ids = []

    text.split(/[\s]+/).each do |token|
      s = token.gsub(/[^:\-A-Za-z0-9_]/, '').upcase

      if s =~ /^[A-Z]{2}[0-9]{2}:[\-A-Z0-9:]+$/ || s =~ /^[\-A-Z0-9_]+_[A-Z]{2}_[0-9]{2}$/
        possible_ids << s
      end
    end


    with_db_connection do |conn|
      courses = []

      possible_ids.each_slice(20) do |values|
        placeholders = values.map { "?" }.join(", ")

        stmnt = conn.prepare_statement("select stem_name, descr from nyu_t_course_catalog" +
                                       " where upper(stem_name) in (#{placeholders})" +
                                       " OR upper(cle_crseid) in (#{placeholders})" +
                                       " order by stem_name")

        with_open(stmnt) do |stmnt|
          (values.count * 2).times do |i|
            stmnt.setString(i + 1, values[i % values.length])
          end

          with_open(stmnt.execute_query) do |rs|
            while rs.next
              courses << "#{rs.getString('STEM_NAME')} -- #{rs.getString('DESCR')}"
            end
          end
        end
      end

      courses
    end
  end


  ## Controllers

  get '/show_users' do
    page = Integer(params[:page])
    netid = params[:netid] && !params[:netid].strip.empty? && params[:netid].strip
    course = params[:course] && !params[:course].strip.empty? && params[:course].strip

    [200, {"Content-Type" => "application/json"},
     [show_users(page, netid, course).to_json]]
  end


  get '/list_departments' do
    session = params[:session].strip
    school = params[:school].strip

    raise "Invalid session" if (session !~ /^[a-zA-Z0-9]+$/)
    raise "Invalid school" if (school !~ /^[A-Z]+$/)

    departments = get_departments_for(session, school)

    [200, {"Content-Type" => "application/json"}, [departments.to_json]]
  end


  get '/list_subjects' do
    session = params[:session].strip
    school = params[:school].strip
    department = params[:department] && !params[:department].strip.empty? && params[:department].strip

    raise "Invalid session" if (session !~ /^[a-zA-Z0-9]+$/)
    raise "Invalid school" if (school !~ /^[A-Z]+$/)

    subjects = get_subjects_for(session, school, department)

    [200, {"Content-Type" => "application/json"}, [subjects.to_json]]
  end


  get '/list_courses' do
    session = params[:session].strip
    school = params[:school].strip
    department = params[:department].strip

    raise "Invalid session" if (session !~ /^[a-zA-Z0-9]+$/)
    raise "Invalid school" if (school !~ /^[A-Z]+$/)

    department = nil if department == ""

    courses = get_courses_for(session, school, department, params[:substring])

    [200, {"Content-Type" => "application/json"}, [courses.to_json]]
  end


  post '/remove_entry' do
    remove_entry(params[:user], params[:course])
  end


  post '/bulk_search' do
    result = bulk_search(params[:text])

    [200, {"Content-Type" => "application/json"}, [result.to_json]]
  end


  post '/apply_update' do
    grant_courses_to_users(params[:courses], params[:users],
                           params[:school], params[:session])
  end


  get '/user_exists' do
    if user_exists?(params[:netid])
      [200, {"Content-Type" => "text/plain"}, []]
    else
      [404, {"Content-Type" => "text/plain"}, []]
    end
  end


  get '/' do
    @sessions = get_academic_sessions.map {|session|
      [session['CODE_4CHAR'], session['DESCR']]
    }

    @schools = get_schools

    erb :main
  end


end

if $0 == __FILE__
  Surrogates.run!
end
