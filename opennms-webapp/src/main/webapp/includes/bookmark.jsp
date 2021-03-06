<%--
/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

--%>

<script type="text/javascript">
<!--
function addbookmark()
{
	if((navigator.appName == "Microsoft Internet Explorer" && (parseInt(navigator.appVersion) >= 4)))
	{
		var url = window.location
		var title = String(window.document.title)
		var title1  = title
		while( title1.indexOf(' | ') != -1  ){
			title1 = title1.replace(' | ' , ' - ');
		}
		javascript:window.external.AddFavorite(url, title1);
	}
	else if(!document.all)
	{
		var msg = "Netscape users must bookmark the pages manually by hitting"
		if(navigator.appName == "Netscape") 
		{
			msg += " <CTRL-D>";
		}
		document.write(msg);
	}
}
//-->
</script>

<div id="include-bookmark">
  <form id="bookmark" action="javascript:addbookmark()">
    <input type="submit" value="Bookmark the results"/>
  </form>
</div>
