#!/usr/bin/python
# -*- coding: utf-8 -*-

# Copyright (c) 2014 ken restivo


# get_server_details lifted from pyicequery https://github.com/fergalmoran/pyicequery
# modified by ken restivo 2014

# Copyright (c) 2013, Fergal Moran
# All rights reserved.
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
# 1. Redistributions of source code must retain the above copyright notice, this
# list of conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice,
# this list of conditions and the following disclaimer in the documentation
# and/or other materials provided with the distribution.
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
# ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
# WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
# ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
# (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
# LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
# ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


import socket
import json
from collections import OrderedDict
import urllib2
import getopt
import sys
import re
from bs4 import BeautifulSoup

def get_server_details(server, port, mount):
    server = "http://%s:%s/status.xsl?mount=/%s" % (server, port, mount)
    try:
        response = urllib2.urlopen(server)
        html = response.read()
        if html:
            soup = BeautifulSoup(html)

            info = {}
            try:
                info['stream_title'] = soup.find(text="Stream Title:").findNext('td').contents[0]
            except:
                pass
            try:
                info['stream_description'] = soup.find(text="Stream Description:").findNext('td').contents[0]
            except:
                pass
            try:
                info['content_type'] = soup.find(text="Content Type:").findNext('td').contents[0]
            except:
                pass
            try:
                info['mount_started'] = soup.find(text="Mount started:").findNext('td').contents[0]
            except:
                pass
            try:
                info['quality'] = soup.find(text="Quality:").findNext('td').contents[0]
            except:
                pass
            try:
                info['current_listeners'] = soup.find(text="Current Listeners:").findNext('td').contents[0]
            except:
                pass
            try:
                info['peak_listeners'] = soup.find(text="Peak Listeners:").findNext('td').contents[0]
            except:
                pass
            try:
                info['stream_genre'] = soup.find(text="Stream Genre:").findNext('td').contents[0]
            except:
                pass
            try:
                info['stream_url'] = soup.find(text="Stream URL:").findNext('td').findNext('a').contents[0]
            except:
                pass
            try:
                info['current_song'] = soup.find(text="Current Song:").findNext('td').contents[0]
            except:
                pass
            return info 
        else:
            print "Invalid content found"
            return None

    except urllib2.URLError:
        print "Unable to read url, please check your parameters"
        return None

def is_not_fucked(m, k):
    return m.has_key(k) and m[k].find('Unspecified') < 0 and m[k] != 'none' and m[k] != '<NULL>' 


def filter_keys(raw):
    s = OrderedDict()
    for k in ['stream_title', 'stream_description', 'stream_genre']:
        if is_not_fucked(raw, k):
            s[k] = raw[k]
    return s

def distill_live_map(oldm):
    m = filter_keys(oldm)
    return u" - ".join(m.values())



def get_live(host, port, mount):
    try:
        pdata  = get_server_details(host, port, mount)
        if len(pdata.keys()) > 0:
            s = distill_live_map(pdata)
            s = u"[LIVE!] " + s
            return  s
    except:
        pass


def known(s):
    return s.strip('- Unknown').strip(" - <NULL>")

def get_song(host, port, mount):
    try:
        pdata  = get_server_details(host, port, mount)
        if len(pdata.keys()) > 0:
            if pdata['current_song'] == "Unknown":
                return None
            s = known(pdata['current_song'])
            return  s
    except:
        pass


#hack required because most of the stuff people upload has no metadata.
def get_archive(host, port, thing):
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect ((host, port))
        s.sendall(thing + '.next\nquit\n')
        data = s.recv(8124).decode('utf-8', 'ignore').encode('utf-8')
        s.close()
        fname=' '.join([l for l in data.split('\n') if l.count('[playing]')][0].split(' ')[1:])
        return  re.sub(r"-\d+kbps", "", fname.split("/")[-1].replace("unknown-","").replace(".ogg","").replace(".mp3",""))
    except:
        pass



def mystery():
    return "IT'S A MYSTERY! Listen and guess."


#

def get_listeners(server, port):
    srv = "http://%s:%s/status.xsl" % (server, port)
    listeners = "unknown"
    try:
        response = urllib2.urlopen(srv)
        html = response.read()
        if html:
            soup = BeautifulSoup(html)
            try:
                listeners = sum([int(j.findNext('td').contents[0]) for j in soup.find_all(text="Current Listeners:")])
            except:
                pass
    except urllib2.URLError:
        print "Unable to read url, please check your parameters"
        return "NetErr"
    return listeners

            
def all_together_now(icecast_host, icecast_port):
    return (get_live(icecast_host, icecast_port, "stream") or
            get_song(icecast_host, icecast_port, "radio") or
            get_archive("localhost", 1234, "archives") or
            mystery())


def all_together_json(icecast_host, icecast_port):
    return json.dumps({"playing":
                       all_together_now(icecast_host, icecast_port),
                       "listeners": get_listeners(icecast_host, icecast_port)})



################################################################################

if __name__ == "__main__":
    if sys.argv[1] == None:
        host = "localhost"
    else:
        host = sys.argv[1]

    if sys.argv[2] == None:
        port = 8050
    else:
        port = sys.argv[2]

    print all_together_json(host, port)




