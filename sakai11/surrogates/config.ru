require 'rubygems'
require_relative './app/main'

def app
  Surrogates
end

map "/" do
  run Surrogates
end
